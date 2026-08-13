### 实际发送给 DeepSeek 的完整 JSON Schema

模型在看到数据库 Schema 和用户问题后，必须按照这个契约返回 JSON。

```json
{
  "$schema" : "https://json-schema.org/draft/2020-12/schema",
  "$defs" : {
    "Aggregate" : {
      "type" : "string",
      "enum" : [ "NONE", "COUNT", "COUNT_DISTINCT", "SUM", "AVG", "MAX", "MIN" ]
    },
    "Operator" : {
      "type" : "string",
      "enum" : [ "EQ", "NE", "GT", "GTE", "LT", "LTE", "LIKE", "IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL", "HOUR_EQ", "HOUR_BETWEEN" ]
    }
  },
  "type" : "object",
  "properties" : {
    "clarification" : {
      "type" : "string",
      "description" : "需要澄清时的问题；READY时为空字符串"
    },
    "distinct" : {
      "type" : "boolean",
      "description" : "是否SELECT DISTINCT"
    },
    "from" : {
      "type" : "object",
      "properties" : {
        "alias" : {
          "type" : "string",
          "description" : "安全英文别名；没有时为空字符串"
        },
        "table" : {
          "type" : "string",
          "description" : "Schema中的真实表名"
        }
      },
      "required" : [ "alias", "table" ],
      "description" : "主表及别名",
      "additionalProperties" : false
    },
    "groupBy" : {
      "description" : "GROUP BY字段引用",
      "type" : "array",
      "items" : {
        "type" : "string"
      }
    },
    "having" : {
      "description" : "HAVING条件",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "aggregate" : {
            "$ref" : "#/$defs/Aggregate"
          },
          "field" : {
            "type" : "string",
            "description" : "字段引用"
          },
          "operator" : {
            "$ref" : "#/$defs/Operator"
          },
          "value" : {
            "type" : "string"
          }
        },
        "required" : [ "aggregate", "field", "operator", "value" ],
        "additionalProperties" : false
      }
    },
    "joins" : {
      "description" : "关联表；不需要时为空数组",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "alias" : {
            "type" : "string",
            "description" : "被关联表的安全英文别名"
          },
          "leftField" : {
            "type" : "string",
            "description" : "已在查询范围内的别名.字段名"
          },
          "rightField" : {
            "type" : "string",
            "description" : "本次关联表的别名.字段名"
          },
          "table" : {
            "type" : "string",
            "description" : "被关联的真实表名"
          },
          "type" : {
            "type" : "string",
            "enum" : [ "INNER", "LEFT" ],
            "description" : "INNER或LEFT"
          }
        },
        "required" : [ "alias", "leftField", "rightField", "table", "type" ],
        "additionalProperties" : false
      }
    },
    "limit" : {
      "type" : "integer",
      "format" : "int32",
      "description" : "最大返回行数"
    },
    "orderBy" : {
      "description" : "ORDER BY规则",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "direction" : {
            "type" : "string",
            "enum" : [ "ASC", "DESC" ]
          },
          "field" : {
            "type" : "string",
            "description" : "字段引用或SELECT别名"
          }
        },
        "required" : [ "direction", "field" ],
        "additionalProperties" : false
      }
    },
    "select" : {
      "description" : "SELECT字段和聚合表达式",
      "type" : "array",
      "items" : {
        "type" : "object",
        "properties" : {
          "aggregate" : {
            "$ref" : "#/$defs/Aggregate",
            "description" : "NONE表示普通字段"
          },
          "alias" : {
            "type" : "string",
            "description" : "结果别名"
          },
          "field" : {
            "type" : "string",
            "description" : "字段引用；多表时使用别名.字段名"
          }
        },
        "required" : [ "aggregate", "alias", "field" ],
        "additionalProperties" : false
      }
    },
    "statementType" : {
      "type" : "string",
      "const" : "SELECT",
      "description" : "固定为SELECT"
    },
    "status" : {
      "type" : "string",
      "enum" : [ "READY", "NEED_CLARIFICATION" ],
      "description" : "READY表示可以查询；NEED_CLARIFICATION表示需要用户补充"
    },
    "where" : {
      "type" : "object",
      "properties" : {
        "conditions" : {
          "type" : "array",
          "items" : {
            "type" : "object",
            "properties" : {
              "field" : {
                "type" : "string",
                "description" : "字段引用"
              },
              "operator" : {
                "$ref" : "#/$defs/Operator"
              },
              "values" : {
                "description" : "参数值列表",
                "type" : "array",
                "items" : {
                  "type" : "string"
                }
              }
            },
            "required" : [ "field", "operator", "values" ],
            "additionalProperties" : false
          }
        },
        "logic" : {
          "type" : "string",
          "enum" : [ "AND", "OR" ]
        }
      },
      "required" : [ "conditions", "logic" ],
      "description" : "WHERE条件组",
      "additionalProperties" : false
    }
  },
  "required" : [ "clarification", "distinct", "from", "groupBy", "having", "joins", "limit", "orderBy", "select", "statementType", "status", "where" ],
  "additionalProperties" : false
}
```


### 逻辑上的完整 JSON 返回模板

下面是模型必须返回的结构：

```json
{
  "status": "READY",
  "clarification": "",
  "statementType": "SELECT",
  "from": {
    "table": "真实表名",
    "alias": "表别名"
  },
  "joins": [
    {
      "type": "INNER",
      "table": "被关联的真实表名",
      "alias": "关联表别名",
      "leftField": "已有别名.字段名",
      "rightField": "新表别名.字段名"
    }
  ],
  "distinct": false,
  "select": [
    {
      "field": "别名.真实字段名",
      "aggregate": "NONE",
      "alias": "结果列别名"
    }
  ],
  "where": {
    "logic": "AND",
    "conditions": [
      {
        "field": "别名.真实字段名",
        "operator": "EQ",
        "values": [
          "条件值"
        ]
      }
    ]
  },
  "groupBy": [
    "别名.真实字段名"
  ],
  "having": [
    {
      "aggregate": "SUM",
      "field": "别名.真实字段名",
      "operator": "GT",
      "value": "比较值"
    }
  ],
  "orderBy": [
    {
      "field": "字段引用或SELECT结果别名",
      "direction": "DESC"
    }
  ],
  "limit": 100
}
```



---

## DeepSeek-v4-falsh 实际返回的 JSON

用户提示词：“找一下电子银行交易记录中在26年7月23号当天转账超过10w的用户，并且查看他当天累计到底转账了多少钱”，DeepSeek 原始返回的用json写得SQL语法抽象树，实质含义是：

SELECT `e`.`cust_no` AS `customer_no`, SUM(`e`.`trad_amt`) AS `total_amount` FROM `ebank_transaction_risk_detail` AS `e` WHERE `e`.`flat_sys_date` = '20260723' GROUP BY `e`.`cust_no` HAVING MAX(`e`.`trad_amt`) > 100000 ORDER BY `total_amount` DESC LIMIT 100

```json
{
  "clarification": "",
  "distinct": false,
  "from": {
    "alias": "e",
    "table": "ebank_transaction_risk_detail"
  },
  "groupBy": [
    "e.cust_no"
  ],
  "having": [
    {
      "aggregate": "MAX",
      "field": "e.trad_amt",
      "operator": "GT",
      "value": "100000"
    }
  ],
  "joins": [],
  "limit": 100,
  "orderBy": [
    {
      "direction": "DESC",
      "field": "total_amount"
    }
  ],
  "select": [
    {
      "aggregate": "NONE",
      "alias": "cust_no",
      "field": "e.cust_no"
    },
    {
      "aggregate": "SUM",
      "alias": "total_amount",
      "field": "e.trad_amt"
    }
  ],
  "statementType": "SELECT",
  "status": "READY",
  "where": {
    "conditions": [
      {
        "field": "e.flat_sys_date",
        "operator": "EQ",
        "values": [
          "20260723"
        ]
      }
    ],
    "logic": "AND"
  }
}
```

### 7.1 每部分含义

#### 主表

```json
{
  "from": {
    "alias": "e",
    "table": "ebank_transaction_risk_detail"
  }
}
```

含义：

```sql
FROM `ebank_transaction_risk_detail` AS `e`
```

#### SELECT

```json
{
  "select": [
    {
      "aggregate": "NONE",
      "alias": "cust_no",
      "field": "e.cust_no"
    },
    {
      "aggregate": "SUM",
      "alias": "total_amount",
      "field": "e.trad_amt"
    }
  ]
}
```

含义：

```sql
SELECT
    `e`.`cust_no` AS `cust_no`,
    SUM(`e`.`trad_amt`) AS `total_amount`
```

#### WHERE

```json
{
  "where": {
    "logic": "AND",
    "conditions": [
      {
        "field": "e.flat_sys_date",
        "operator": "EQ",
        "values": [
          "20260723"
        ]
      }
    ]
  }
}
```

含义：

```sql
WHERE `e`.`flat_sys_date` = :p1
```

参数：

```json
{
  "p1": "20260723"
}
```

因为 `flat_sys_date` 是 `CHAR(8)`，所以 Java 保持字符串类型，不将其转换成数字。

#### GROUP BY

```json
{
  "groupBy": [
    "e.cust_no"
  ]
}
```

含义：

```sql
GROUP BY `e`.`cust_no`
```

这样可以按客户汇总当天全部交易。

#### HAVING

```json
{
  "having": [
    {
      "aggregate": "MAX",
      "field": "e.trad_amt",
      "operator": "GT",
      "value": "100000"
    }
  ]
}
```

含义：

```sql
HAVING MAX(`e`.`trad_amt`) > :p2
```

参数：

```json
{
  "p2": 100000
}
```

因为 `trad_amt` 是 `DECIMAL(20,2)`，Java 会把字符串 `"100000"` 转换为 `BigDecimal`。

这个条件的业务含义是：

> 客户当天至少存在一笔金额超过 10 万的交易。

它不是：

> 客户当天累计交易金额超过 10 万。

如果业务要求“当天累计超过 10 万”，正确 AST 应该是：

```json
{
  "aggregate": "SUM",
  "field": "e.trad_amt",
  "operator": "GT",
  "value": "100000"
}
```

对应 SQL：

```sql
HAVING SUM(`e`.`trad_amt`) > :p2
```

因此，本案例中的 `MAX` 还是 `SUM` 是一个业务语义问题。后端能确认字段真实存在、SQL结构安全，但当前无法仅凭类型校验判断用户口径应该是“单笔超过”还是“累计超过”。

#### ORDER BY

```json
{
  "orderBy": [
    {
      "direction": "DESC",
      "field": "total_amount"
    }
  ]
}
```

`total_amount` 是 SELECT 中定义的结果别名，因此编译为：

```sql
ORDER BY `total_amount` DESC
```

#### LIMIT

```json
{
  "limit": 100
}
```

含义：

```sql
LIMIT 100
```


请统计20260723到20260730各IP国家的交易笔数和占比，并用饼图展示。
请按交易渠道统计20260723到20260730的交易笔数，按照交易笔数从高到低排列，并用柱状图展示。
## 后端分层结构

后端已按技术层组织，主要目录如下：

```text
com.xym.antifraud
├─ controller   HTTP 接口层
├─ service      业务逻辑层
├─ mapper       MyBatis Mapper 接口层
├─ entity       数据库实体层
├─ dto          请求与响应对象
├─ config       Spring、数据源和 MyBatis 配置
├─ exception    业务异常
├─ query        Text2SQL 编译与校验组件
├─ schema       数据库 Schema 读取组件
└─ datasource   MySQL/Hive 数据源路由组件
```

Mapper SQL 全部放在 `src/main/resources/mapper/*.xml`，Java Mapper 接口中不再写注解 SQL。

## 登录与注册

- `POST /api/auth/register`：`multipart/form-data`，字段为 `username`、`phone`、`password`、`avatarFile`。
- `POST /api/auth/login`：JSON，字段为 `phone`、`password`。
- 两个接口成功后都返回 `id`、`username`、`phone`、`avatarPath`、`token`。
- 密码使用 BCrypt 哈希后入库；头像默认保存到前端 `src/asserts`，MySQL 的 `avatar_path` 只记录 `src/asserts/<文件名>` 相对路径，可用环境变量 `AVATAR_DIRECTORY` 修改实际存放目录。
- 首次运行前请在业务 MySQL 库执行 `src/main/resources/db/schema-config.sql`，其中包含 `users` 及 Schema 配置表。
