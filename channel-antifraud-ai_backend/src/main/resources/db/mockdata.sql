INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, acct_no, acct_type, acct_bran, cust_typ, cust_grd, cert_typ, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, ret_code, curr, trad_amt, order_amt, trad_bran, trad_type, ass_acct_no, ass_acct_type, ass_branno, merc_id, merc_name, sub_shop_id, auth_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('20260723000037_101005D341784736036108489', 'T5200000000008179624', '6217007100029560000', '2', '520615900', 'C', '2', null, '20260723000037_101005D341784736036108489', '20260723', '37', '2', 'P1DBZZ000', '0', null, 'RMB', 147823.00, 0.00, '520000000', null, '4367427101170490000', '2', '520433600', null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 147823.00, '2026-07-30 16:44:08');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, acct_no, acct_type, acct_bran, cust_typ, cust_grd, cert_typ, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, ret_code, curr, trad_amt, order_amt, trad_bran, trad_type, ass_acct_no, ass_acct_type, ass_branno, merc_id, merc_name, sub_shop_id, auth_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('20260723000058_101005N291784736058464605', 'P0944190000103423178', null, null, null, null, null, null, '20260723000058_101005N291784736058464606', '20260723', '58', '2', 'AP101ZX31', '0', null, null, null, null, null, null, null, null, null, null, null, null, null, '2409:8962:d38:138f:3cc4:fbff:fe35:7389', '0', null, '1', 'CN', '51', '28', null, 1, 0, null, null, null, '2026-07-30 16:44:08');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, acct_no, acct_type, acct_bran, cust_typ, cust_grd, cert_typ, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, ret_code, curr, trad_amt, order_amt, trad_bran, trad_type, ass_acct_no, ass_acct_type, ass_branno, merc_id, merc_name, sub_shop_id, auth_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('20260723000058_101005N291784736058464606', 'P0944190000103423178', null, null, null, null, null, null, '20260723000058_101005N291784736058464606', '20260723', '58', '2', 'AP101ZX31', '0', null, null, null, null, null, null, null, null, null, null, null, null, null, '2409:8962:d38:138f:3cc4:fbff:fe35:7389', '0', null, '1', 'CN', '51', '28', null, 1, 0, null, null, null, '2026-07-30 16:43:30');
-- 报表测试数据：覆盖日期趋势、渠道对比、国家占比和交易金额分布
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260724_001', 'MOCK_CUST_001', 'MOCK_FLOW_20260724_001', '20260724', '091500', '1', 'TRANSFER', '0', 'RMB', 12800.00, 12800.00, 'TRANSFER', '10.10.1.24', 'MOCK_DEVICE_A', 'MOBILE', '0', 'CN', '11', '1101', 0, 1, 2, 0, 0, 12800.00, '2026-07-24 09:15:00');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260725_002', 'MOCK_CUST_002', 'MOCK_FLOW_20260725_002', '20260725', '103000', '2', 'PAYMENT', '0', 'RMB', 35600.00, 35000.00, 'PAYMENT', '10.10.2.25', 'MOCK_DEVICE_B', 'PC', '0', 'CN', '31', '3101', 1, 2, 5, 1, 0, 35600.00, '2026-07-25 10:30:00');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260726_003', 'MOCK_CUST_003', 'MOCK_FLOW_20260726_003', '20260726', '114500', '3', 'TRANSFER', '0', 'RMB', 8900.00, 8900.00, 'TRANSFER', '203.0.113.26', 'MOCK_DEVICE_C', 'MOBILE', '1', 'US', 'CA', 'LAX', 3, 4, 1, 1, 2, 8900.00, '2026-07-26 11:45:00');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260727_004', 'MOCK_CUST_004', 'MOCK_FLOW_20260727_004', '20260727', '132000', '4', 'PAYMENT', '1', 'RMB', 76200.00, 75000.00, 'PAYMENT', '10.10.4.27', 'MOCK_DEVICE_D', 'TABLET', '0', 'CN', '44', '4401', 2, 6, 8, 0, 0, 76200.00, '2026-07-27 13:20:00');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260728_005', 'MOCK_CUST_005', 'MOCK_FLOW_20260728_005', '20260728', '150500', '1', 'TRANSFER', '0', 'RMB', 21500.00, 21000.00, 'TRANSFER', '198.51.100.28', 'MOCK_DEVICE_E', 'MOBILE', '1', 'SG', 'SG', 'SG', 4, 3, 3, 1, 3, 21500.00, '2026-07-28 15:05:00');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260729_006', 'MOCK_CUST_006', 'MOCK_FLOW_20260729_006', '20260729', '172500', '3', 'PAYMENT', '0', 'RMB', 98400.00, 98000.00, 'PAYMENT', '10.10.3.29', 'MOCK_DEVICE_F', 'PC', '0', 'CN', '51', '5101', 1, 8, 12, 0, 0, 98400.00, '2026-07-29 17:25:00');
INSERT INTO cfrm_agent.ebank_transaction_risk_detail (i_flow_no, cust_no, flow_no, flat_sys_date, flat_trad_time, chanl_no, chanl_trad_no, trad_sts, curr, trad_amt, order_amt, trad_type, ip, equip_id, equip_type, ip_gpbflag, ipcountry, ippriv, ipcity, password_error_count, ip_customer_count_7d, device_customer_count_30d, new_device_flag, overseas_ip_login_count_1d, successful_transfer_amount_1d, created_at) VALUES ('MOCK_REPORT_20260730_007', 'MOCK_CUST_007', 'MOCK_FLOW_20260730_007', '20260730', '201000', '4', 'TRANSFER', '0', 'RMB', 43800.00, 43000.00, 'TRANSFER', '203.0.113.30', 'MOCK_DEVICE_G', 'MOBILE', '1', 'US', 'NY', 'NYC', 5, 5, 6, 1, 4, 43800.00, '2026-07-30 20:10:00');

CREATE TABLE `ebank_transaction_risk_detail` (
  `i_flow_no` varchar(64) NOT NULL COMMENT '内部流水号',
  `cust_no` varchar(32) NOT NULL COMMENT '平台客户号',
  `acct_no` varchar(64) DEFAULT NULL COMMENT '账号',
  `acct_type` varchar(16) DEFAULT NULL COMMENT '主账号类型',
  `acct_bran` varchar(32) DEFAULT NULL COMMENT '开户机构',
  `cust_typ` varchar(16) DEFAULT NULL COMMENT '客户类型',
  `cust_grd` varchar(16) DEFAULT NULL COMMENT '客户级别',
  `cert_typ` varchar(16) DEFAULT NULL COMMENT '证件类型',
  `flow_no` varchar(64) DEFAULT NULL COMMENT '流水号',
  `flat_sys_date` char(8) NOT NULL COMMENT '交易日期',
  `flat_trad_time` char(6) DEFAULT NULL COMMENT '交易时间',
  `chanl_no` varchar(16) NOT NULL COMMENT '交易渠道',
  `chanl_trad_no` varchar(32) DEFAULT NULL COMMENT '渠道交易码',
  `trad_sts` varchar(8) NOT NULL COMMENT '交易标志',
  `ret_code` varchar(16) DEFAULT NULL COMMENT '响应码',
  `curr` varchar(8) DEFAULT NULL COMMENT '币种',
  `trad_amt` decimal(20,2) DEFAULT NULL COMMENT '交易金额',
  `order_amt` decimal(20,2) DEFAULT NULL COMMENT '订单金额',
  `trad_bran` varchar(32) DEFAULT NULL COMMENT '交易机构',
  `trad_type` varchar(32) DEFAULT NULL COMMENT '交易子类型',
  `ass_acct_no` varchar(64) DEFAULT NULL COMMENT '辅账号',
  `ass_acct_type` varchar(16) DEFAULT NULL COMMENT '辅账号类型',
  `ass_branno` varchar(32) DEFAULT NULL COMMENT '辅账号机构',
  `merc_id` varchar(64) DEFAULT NULL COMMENT '商户编号',
  `merc_name` varchar(256) DEFAULT NULL COMMENT '商户名称',
  `sub_shop_id` varchar(64) DEFAULT NULL COMMENT '二级商户编号',
  `auth_type` varchar(16) DEFAULT NULL COMMENT '验证类型',
  `ip` varchar(64) DEFAULT NULL COMMENT '标准IP',
  `equip_id` varchar(128) DEFAULT NULL COMMENT '终端设备',
  `equip_type` varchar(32) DEFAULT NULL COMMENT '终端设备类型',
  `ip_gpbflag` varchar(8) DEFAULT NULL COMMENT 'IP国内外',
  `ipcountry` varchar(8) DEFAULT NULL COMMENT 'IP国家',
  `ippriv` varchar(16) DEFAULT NULL COMMENT 'IP省',
  `ipcity` varchar(16) DEFAULT NULL COMMENT 'IP城市',
  `password_error_count` bigint DEFAULT NULL COMMENT '输错密码次数',
  `ip_customer_count_7d` bigint DEFAULT NULL COMMENT '当前登录IP7天内登录客户个数',
  `device_customer_count_30d` bigint DEFAULT NULL COMMENT '同一设备30天内登陆客户数',
  `new_device_flag` smallint DEFAULT NULL COMMENT '新设备（建行生活）',
  `overseas_ip_login_count_1d` bigint DEFAULT NULL COMMENT '当天境外IP登录次数',
  `successful_transfer_amount_1d` decimal(20,2) DEFAULT NULL COMMENT '当天累计成功转账金额',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`i_flow_no`),
  KEY `idx_ebank_risk_cust_date` (`cust_no`,`flat_sys_date`),
  KEY `idx_ebank_risk_acct_date` (`acct_no`,`flat_sys_date`),
  KEY `idx_ebank_risk_device` (`equip_id`),
  KEY `idx_ebank_risk_ip` (`ip`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='电子银行交易业务明细'
