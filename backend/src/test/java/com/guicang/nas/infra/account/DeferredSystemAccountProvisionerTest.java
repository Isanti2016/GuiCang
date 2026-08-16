package com.guicang.nas.infra.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 延迟供给实现测试：helper 未部署时所有操作返回 PENDING。 */
class DeferredSystemAccountProvisionerTest {

  private final DeferredSystemAccountProvisioner provisioner =
      new DeferredSystemAccountProvisioner();

  @Test
  void 所有操作返回PENDING() {
    assertThat(provisioner.provisionAdmin("admin").status()).isEqualTo(ProvisionStatus.PENDING);
    assertThat(provisioner.createUser("alice", "pass").status()).isEqualTo(ProvisionStatus.PENDING);
    assertThat(provisioner.deleteUser("alice", false).status()).isEqualTo(ProvisionStatus.PENDING);
    assertThat(provisioner.setUserStatus("alice", true).status())
        .isEqualTo(ProvisionStatus.PENDING);
    assertThat(provisioner.setUserPassword("alice", "pass").status())
        .isEqualTo(ProvisionStatus.PENDING);
  }
}
