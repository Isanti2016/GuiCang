package com.guicang.nas.module.camera;

import com.guicang.nas.common.Result;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.infra.storage.StorageService;
import com.guicang.nas.module.camera.dto.CameraRecordVO;
import com.guicang.nas.module.camera.dto.CameraVO;
import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 监控摄像头接口：摄像头维护 + 录像查询（播放走 /files/stream 流式接口）。 */
@RestController
@RequestMapping("/api/v1/cameras")
@Validated
public class CameraController {

  private final CameraService cameraService;
  private final StorageService storageService;

  public CameraController(CameraService cameraService, StorageService storageService) {
    this.cameraService = cameraService;
    this.storageService = storageService;
  }

  /** 摄像头列表（含录像统计）。 */
  @GetMapping
  public Result<List<CameraVO>> list() {
    return Result.ok(cameraService.listAll());
  }

  /** 目录约定信息（接收/归档目录真实路径，供前端展示接入说明）。 */
  @GetMapping("/meta")
  public Result<Map<String, String>> meta() {
    Path root = storageService.root();
    return Result.ok(
        Map.of(
            "receiveDir", root.resolve("cameras/incoming").toString(),
            "archiveDir", root.resolve("cameras/archive").toString()));
  }

  /** 新增或更新摄像头。 */
  @PostMapping
  @Audit(action = "camera.save", resource = "#camera?.name ?: ''")
  public Result<Camera> save(@RequestBody Camera camera) {
    return Result.ok(cameraService.save(camera));
  }

  /** 删除摄像头注册信息（不删除录像）。 */
  @DeleteMapping("/{id}")
  @Audit(action = "camera.delete", resource = "#id")
  public Result<Void> delete(@PathVariable Long id) {
    cameraService.delete(id);
    return Result.ok();
  }

  /** 某摄像头某日期的录像列表。 */
  @GetMapping("/records")
  public Result<List<CameraRecordVO>> records(
      @RequestParam @NotBlank String camera,
      @RequestParam(required = false) String date) {
    return Result.ok(cameraService.records(camera, date));
  }
}
