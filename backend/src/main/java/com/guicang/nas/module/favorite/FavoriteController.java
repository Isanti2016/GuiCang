package com.guicang.nas.module.favorite;

import com.guicang.nas.common.Result;
import com.guicang.nas.module.favorite.dto.FavoriteVO;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 文件收藏接口。 */
@RestController
@RequestMapping("/api/v1/favorites")
@Validated
public class FavoriteController {

  private final FavoriteService favoriteService;

  public FavoriteController(FavoriteService favoriteService) {
    this.favoriteService = favoriteService;
  }

  @PostMapping
  public Result<Void> add(@RequestBody Map<String, String> body) {
    favoriteService.add(body.get("path"));
    return Result.ok();
  }

  @DeleteMapping
  public Result<Void> remove(@RequestParam @NotBlank String path) {
    favoriteService.remove(path);
    return Result.ok();
  }

  @GetMapping
  public Result<List<FavoriteVO>> list() {
    return Result.ok(favoriteService.list());
  }
}
