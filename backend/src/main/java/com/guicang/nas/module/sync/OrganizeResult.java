package com.guicang.nas.module.sync;

import java.util.ArrayList;
import java.util.List;

/** 自动整理执行结果。 */
public record OrganizeResult(
    int processed, int succeeded, int failed, int skipped, List<String> errors) {

  public static OrganizeResult empty() {
    return new OrganizeResult(0, 0, 0, 0, new ArrayList<>());
  }
}
