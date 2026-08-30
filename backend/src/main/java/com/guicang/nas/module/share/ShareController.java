package com.guicang.nas.module.share;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.file.FileStreamResponder;
import com.guicang.nas.module.share.dto.ShareCreateRequest;
import com.guicang.nas.module.share.dto.ShareVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 分享链接接口：创建/列表/撤销需登录；下载免登录（token + 可选密码）。 */
@RestController
@RequestMapping("/api/v1/shares")
@Validated
public class ShareController {

  private final ShareService shareService;
  private final FileStreamResponder fileStreamResponder;

  public ShareController(ShareService shareService, FileStreamResponder fileStreamResponder) {
    this.shareService = shareService;
    this.fileStreamResponder = fileStreamResponder;
  }

  @PostMapping
  public Result<ShareVO> create(@Valid @RequestBody ShareCreateRequest request) {
    return Result.ok(shareService.create(request.path(), request.password(), request.expireDays()));
  }

  @GetMapping
  public Result<List<ShareVO>> list() {
    return Result.ok(shareService.list());
  }

  @DeleteMapping("/{token}")
  public Result<Void> revoke(@PathVariable String token) {
    shareService.revoke(token);
    return Result.ok();
  }

  /** 免登录下载：GET /shares/{token}/download?pwd=xxx */
  @GetMapping("/{token}/download")
  public ResponseEntity<?> download(
      @PathVariable String token,
      @RequestParam(required = false) String pwd,
      HttpServletRequest request) throws IOException {
    return fileStreamResponder.respond(shareService.download(token, pwd), request, "attachment");
  }
}
