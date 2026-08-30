package com.guicang.nas.module.camera;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.module.camera.dto.CameraRecordVO;
import com.guicang.nas.module.camera.dto.CameraVO;
import com.guicang.nas.module.file.FileIndex;
import com.guicang.nas.module.file.FileIndexService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 监控摄像头服务实现：注册信息读写 + 基于文件索引的录像查询。 */
@Service
public class CameraServiceImpl implements CameraService {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final CameraMapper cameraMapper;
  private final FileIndexService fileIndexService;

  public CameraServiceImpl(CameraMapper cameraMapper, FileIndexService fileIndexService) {
    this.cameraMapper = cameraMapper;
    this.fileIndexService = fileIndexService;
  }

  @Override
  public List<CameraVO> listAll() {
    return cameraMapper.selectList(null).stream()
        .map(
            c -> {
              List<FileIndex> items =
                  fileIndexService.listByPrefix(
                      CameraArchiveJob.ARCHIVE_PREFIX + "/" + c.getName());
              long count = items.stream().filter(CameraServiceImpl::isVideo).count();
              long last =
                  items.stream()
                      .filter(CameraServiceImpl::isVideo)
                      .mapToLong(FileIndex::getMtime)
                      .max()
                      .orElse(0L);
              return new CameraVO(c.getId(), c.getName(), c.getLocation(), count, last);
            })
        .toList();
  }

  @Override
  @Transactional
  public Camera save(Camera camera) {
    if (camera == null || camera.getName() == null || camera.getName().isBlank()) {
      throw new BizException("摄像头名称不能为空");
    }
    String name = camera.getName().trim();
    long now = System.currentTimeMillis();
    if (camera.getId() == null) {
      if (findByKey(name) != null) {
        throw new BizException("摄像头已存在: " + name);
      }
      Camera created = new Camera();
      created.setName(name);
      created.setLocation(camera.getLocation());
      created.setCreatedAt(now);
      created.setUpdatedAt(now);
      cameraMapper.insert(created);
      return created;
    }
    Camera existing = cameraMapper.selectById(camera.getId());
    if (existing == null) {
      throw new BizException("摄像头不存在");
    }
    existing.setName(name);
    existing.setLocation(camera.getLocation());
    existing.setUpdatedAt(now);
    cameraMapper.updateById(existing);
    return existing;
  }

  @Override
  public void delete(Long id) {
    cameraMapper.deleteById(id);
  }

  @Override
  @Transactional
  public void autoRegister(String name) {
    if (name == null || name.isBlank() || findByKey(name) != null) {
      return;
    }
    Camera camera = new Camera();
    camera.setName(name);
    long now = System.currentTimeMillis();
    camera.setCreatedAt(now);
    camera.setUpdatedAt(now);
    try {
      cameraMapper.insert(camera);
    } catch (Exception ignored) {
      // 并发重复注册时忽略（名称唯一约束兜底）
    }
  }

  @Override
  public List<CameraRecordVO> records(String camera, String date) {
    if (camera == null || camera.isBlank()) {
      throw new BizException("请指定摄像头");
    }
    String day = (date == null || date.isBlank()) ? LocalDate.now().format(DATE_FMT) : date;
    List<FileIndex> items =
        fileIndexService.listByPrefix(
            CameraArchiveJob.ARCHIVE_PREFIX + "/" + camera + "/" + day);
    return items.stream()
        .filter(CameraServiceImpl::isVideo)
        .map(
            f ->
                new CameraRecordVO(
                    f.getId(), f.getPath(), f.getName(), f.getSize(), f.getMtime()))
        .toList();
  }

  private Camera findByKey(String name) {
    return cameraMapper.selectOne(
        new LambdaQueryWrapper<Camera>().eq(Camera::getName, name));
  }

  private static boolean isVideo(FileIndex f) {
    return "video".equals(f.getKind())
        || (f.getExt() != null && CameraArchiveJob.isVideoExt(f.getExt()));
  }
}
