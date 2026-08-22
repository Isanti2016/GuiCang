package com.guicang.nas.module.setup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guicang.nas.common.BizException;
import com.guicang.nas.common.audit.Audit;
import com.guicang.nas.infra.account.ProvisionStatus;
import com.guicang.nas.infra.account.SystemAccountProvisioner;
import com.guicang.nas.module.setup.dto.SetupInitRequest;
import com.guicang.nas.module.setup.dto.SetupStatusResponse;
import com.guicang.nas.module.user.SysRole;
import com.guicang.nas.module.user.SysRoleMapper;
import com.guicang.nas.module.user.SysUser;
import com.guicang.nas.module.user.SysUserMapper;
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
  private static final String ROLE_CODE_ADMIN = "admin";

  private final SysConfigMapper sysConfigMapper;
  private final SystemAccountProvisioner systemAccountProvisioner;
  private final SysUserMapper sysUserMapper;
  private final SysRoleMapper sysRoleMapper;

  @Value("${guicang.storage.root}")
  private String storageRoot;

  public SetupServiceImpl(
      SysConfigMapper sysConfigMapper,
      SystemAccountProvisioner systemAccountProvisioner,
      SysUserMapper sysUserMapper,
      SysRoleMapper sysRoleMapper) {
    this.sysConfigMapper = sysConfigMapper;
    this.systemAccountProvisioner = systemAccountProvisioner;
    this.sysUserMapper = sysUserMapper;
    this.sysRoleMapper = sysRoleMapper;
  }

  /**
   * 当前初始化状态。
   *
   * @return 初始化状态（是否完成 + 存储根路径）
   */
  @Override
  public SetupStatusResponse status() {
    return new SetupStatusResponse(isInitialized(), storageRoot);
  }

  /**
   * 执行初始化（新建管理员元数据 + 触发系统账号供给 + 写完成标记；重复初始化被拒绝）。
   *
   * @param request 初始化请求（管理员用户名/显示名）
   * @return 初始化后的状态（已置为完成）
   */
  @Override
  @Transactional
  @Audit(action = "setup.init", resource = "#request.username()")
  public SetupStatusResponse init(SetupInitRequest request) {
    if (isInitialized()) {
      throw new BizException("系统已完成初始化，不能重复初始化");
    }
    // 触发管理员系统账号供给（helper 未接入时为 PENDING，Step 2.1 后落地）
    var provision = systemAccountProvisioner.provisionAdmin(request.username());
    // 写入 admin 的 Web 元数据（角色 admin，密码由系统账号管理）
    SysRole adminRole =
        sysRoleMapper.selectOne(
            new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, ROLE_CODE_ADMIN));
    if (adminRole != null
        && sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.username()))
            == 0) {
      SysUser admin = new SysUser();
      admin.setUsername(request.username());
      admin.setDisplayName(request.displayName());
      admin.setEnabled(1);
      admin.setRoleId(adminRole.getId());
      admin.setHomePath("/home/" + request.username());
      String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
      admin.setCreatedAt(now);
      admin.setUpdatedAt(now);
      sysUserMapper.insert(admin);
    }
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
