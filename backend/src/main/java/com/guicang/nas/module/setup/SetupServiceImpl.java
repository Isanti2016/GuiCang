package com.guicang.nas.module.setup;

import com.guicang.nas.common.BizException;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.infra.account.ProvisionStatus;
import com.guicang.nas.infra.account.SystemAccountProvisioner;
import com.guicang.nas.module.setup.dto.SetupInitRequest;
import com.guicang.nas.module.setup.dto.SetupStatusResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 首次初始化服务实现。 */
@Service
public class SetupServiceImpl implements SetupService {

  private static final String KEY_SETUP_DONE = "setup.done";
  private static final String KEY_ADMIN_USERNAME = "setup.admin.username";
  private static final String KEY_ADMIN_PENDING = "setup.admin.pending";
  private static final String VALUE_TRUE = "1";

  private final SysConfigMapper sysConfigMapper;
  private final SystemAccountProvisioner systemAccountProvisioner;

  @Value("${guicang.storage.root}")
  private String storageRoot;

  public SetupServiceImpl(
      SysConfigMapper sysConfigMapper, SystemAccountProvisioner systemAccountProvisioner) {
    this.sysConfigMapper = sysConfigMapper;
    this.systemAccountProvisioner = systemAccountProvisioner;
  }

  @Override
  public SetupStatusResponse status() {
    return new SetupStatusResponse(isInitialized(), storageRoot);
  }

  @Override
  @Transactional
  @Audit(action = "setup.init", resource = "#request.username()")
  public SetupStatusResponse init(SetupInitRequest request) {
    if (isInitialized()) {
      throw new BizException("系统已完成初始化，不能重复初始化");
    }
    // 触发管理员系统账号供给（helper 未接入时为 PENDING，Step 2.1 后落地）
    var provision = systemAccountProvisioner.provisionAdmin(request.username());
    saveConfig(KEY_SETUP_DONE, VALUE_TRUE);
    saveConfig(KEY_ADMIN_USERNAME, request.username());
    saveConfig(KEY_ADMIN_PENDING, provision.status() == ProvisionStatus.CREATED ? "0" : VALUE_TRUE);
    return new SetupStatusResponse(true, storageRoot);
  }

  private boolean isInitialized() {
    SysConfig config = sysConfigMapper.selectById(KEY_SETUP_DONE);
    return config != null && VALUE_TRUE.equals(config.getValue());
  }

  private void saveConfig(String key, String value) {
    SysConfig config = sysConfigMapper.selectById(key);
    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    if (config == null) {
      config = new SysConfig();
      config.setKey(key);
      config.setValue(value);
      config.setUpdatedAt(now);
      sysConfigMapper.insert(config);
    } else {
      config.setValue(value);
      config.setUpdatedAt(now);
      sysConfigMapper.updateById(config);
    }
  }
}
