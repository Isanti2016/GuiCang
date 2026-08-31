package com.guicang.nas.module.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guicang.nas.module.file.dto.MediaMetadataVO;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 视频元数据探测服务：调 ffprobe 解析媒体流。短时并发去重（同 path 多次同时探测只跑一次）。
 *
 * <p>浏览器音视频原生支持白名单参考 MDN MediaCapabilities.decodingInfo。eac3 / ac3 / dts / truehd
 * 等高清音频编码在 Chrome/Edge 不解码，会导致「画面有声音无」——前端据此提示用户下载后用 VLC 播放。
 */
@Service
public class MediaInspectService {

  private static final Logger log = LoggerFactory.getLogger(MediaInspectService.class);

  /** 浏览器原生支持的音频编码（HTML5 <video>/<audio>）。 */
  static final Set<String> AUDIO_SUPPORTED =
      Set.of(
          "aac", "mp3", "opus", "vorbis", "flac",
          "pcm_mulaw", "pcm_alaw", "pcm_s8", "pcm_u8",
          "pcm_s16le", "pcm_s16be", "pcm_s24le", "pcm_s24be",
          "pcm_s32le", "pcm_s32be", "pcm_f32le", "pcm_f64le");

  /** 浏览器原生支持的视频编码。 */
  static final Set<String> VIDEO_SUPPORTED =
      Set.of("h264", "vp8", "vp9", "av1", "theora");

  private final ObjectMapper objectMapper;
  private final MediaMetadataCacheMapper cacheMapper;

  /** 同 path 并发去重，避免一次播放触发多次 ffprobe（CPU 与磁盘 I/O 双重消耗）。 */
  private final ConcurrentHashMap<String, CompletableFuture<MediaMetadataVO>> inflight = new ConcurrentHashMap<>();

  public MediaInspectService(ObjectMapper objectMapper, MediaMetadataCacheMapper cacheMapper) {
    this.objectMapper = objectMapper;
    this.cacheMapper = cacheMapper;
  }

  /**
   * 探测媒体元数据。失败时（ffprobe 不在 / 文件损坏 / 不是媒体）返回带错误标志的结果，由前端决定是否提示。
   *
   * @param file 媒体文件绝对路径
   * @return 元数据视图对象
   */
  public MediaMetadataVO probe(Path file) {
    String key = file.toAbsolutePath().toString();
    return inflight
        .computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> doProbe(file)))
        .join();
  }

  private MediaMetadataVO doProbe(Path file) {
    String json = "";
    int code = -1;
    try {
      ProcessBuilder pb =
          new ProcessBuilder(
              "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", file.toString());
      pb.redirectErrorStream(true);
      Process p = pb.start();
      StringBuilder sb = new StringBuilder();
      try (var br = new java.io.BufferedReader(
          new java.io.InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
      }
      code = p.waitFor();
      json = sb.toString();
      if (code != 0 || json.isBlank()) {
        return errorResult("ffprobe_exit_" + code);
      }
      JsonNode root = objectMapper.readTree(json);
      if (root == null) return errorResult("ffprobe_empty");

      List<String> audios = new ArrayList<>();
      List<String> videos = new ArrayList<>();
      boolean hasSubtitle = false;
      for (JsonNode s : root.path("streams")) {
        String type = s.path("codec_type").asText("");
        String codec = s.path("codec_name").asText("");
        if (codec.isEmpty()) continue;
        switch (type) {
          case "video" -> videos.add(codec);
          case "audio" -> audios.add(codec);
          case "subtitle" -> hasSubtitle = true;
          default -> {}
        }
      }
      JsonNode fmt = root.path("format");
      String container = fmt.path("format_name").asText("");
      long duration = (long) Math.floor(fmt.path("duration").asDouble(0));
      int width = 0;
      int height = 0;
      // 仅取第一个视频轨的宽高（前端只关心主轨是否兼容）
      for (JsonNode s : root.path("streams")) {
        if ("video".equals(s.path("codec_type").asText(""))) {
          width = s.path("width").asInt(0);
          height = s.path("height").asInt(0);
          break;
        }
      }

      String vc = videos.isEmpty() ? "" : videos.get(0);
      String ac = audios.isEmpty() ? "" : audios.get(0);
      Boolean ba = ac.isEmpty() ? null : AUDIO_SUPPORTED.contains(ac);
      Boolean bv = vc.isEmpty() ? null : VIDEO_SUPPORTED.contains(vc);
      return new MediaMetadataVO(
          container, vc, ac, audios, videos, duration, width, height, hasSubtitle, ba, bv);
    } catch (Exception e) {
      log.warn("ffprobe 失败 (code={}): {} -> {}", code, file, e.toString());
      return errorResult("ffprobe_error");
    } finally {
      inflight.remove(file.toAbsolutePath().toString());
    }
  }

  private MediaMetadataVO errorResult(String code) {
    return new MediaMetadataVO(
        code, "", "", List.of(), List.of(), 0, 0, 0, false, null, null);
  }

  /** 从专用缓存表读取（命中即返，未命中返 null）。 */
  public MediaMetadataVO readFromCache(String relativePath) {
    MediaMetadataCache row = cacheMapper.selectById(relativePath);
    if (row == null) return null;
    Boolean ba = (row.getAudioCodec() == null || row.getAudioCodec().isBlank())
        ? null
        : AUDIO_SUPPORTED.contains(row.getAudioCodec());
    Boolean bv = (row.getVideoCodec() == null || row.getVideoCodec().isBlank())
        ? null
        : VIDEO_SUPPORTED.contains(row.getVideoCodec());
    java.util.List<String> audios = parseListOrEmpty(row.getAudioCodecs());
    java.util.List<String> videos = parseListOrEmpty(row.getVideoCodecs());
    return new MediaMetadataVO(
        row.getContainer() == null ? "" : row.getContainer(),
        row.getVideoCodec() == null ? "" : row.getVideoCodec(),
        row.getAudioCodec() == null ? "" : row.getAudioCodec(),
        audios,
        videos,
        row.getDurationSec() == null ? 0L : row.getDurationSec(),
        row.getWidth() == null ? 0 : row.getWidth(),
        row.getHeight() == null ? 0 : row.getHeight(),
        row.getHasSubtitle() != null && row.getHasSubtitle() == 1,
        ba,
        bv);
  }

  /** 写入/覆盖缓存。空字符串也写入，等价「已探测」标记。 */
  public void writeToCache(String relativePath, MediaMetadataVO vo) {
    try {
      MediaMetadataCache row = new MediaMetadataCache();
      row.setPath(relativePath);
      row.setContainer(vo.container());
      row.setVideoCodec(vo.videoCodec());
      row.setAudioCodec(vo.audioCodec());
      row.setDurationSec(vo.durationSec() == 0 ? null : vo.durationSec());
      row.setWidth(vo.width());
      row.setHeight(vo.height());
      row.setAudioCodecs(toJsonArray(vo.audioCodecs()));
      row.setVideoCodecs(toJsonArray(vo.videoCodecs()));
      row.setHasSubtitle(vo.hasSubtitle() ? 1 : 0);
      row.setProbedAt(System.currentTimeMillis());
      cacheMapper.insert(row);
    } catch (Exception e) {
      // 失败不致命：探测结果已返给前端，下次会再探测
      log.warn("写入媒体缓存失败 ({}): {}", relativePath, e.toString());
    }
  }

  private java.util.List<String> parseListOrEmpty(String json) {
    if (json == null || json.isBlank()) return java.util.List.of();
    try {
      return objectMapper.readValue(json, objectMapper.getTypeFactory()
          .constructCollectionType(java.util.List.class, String.class));
    } catch (Exception e) {
      return java.util.List.of();
    }
  }

  private String toJsonArray(java.util.List<String> list) {
    if (list == null || list.isEmpty()) return "[]";
    try {
      return objectMapper.writeValueAsString(list);
    } catch (Exception e) {
      return "[]";
    }
  }
}
