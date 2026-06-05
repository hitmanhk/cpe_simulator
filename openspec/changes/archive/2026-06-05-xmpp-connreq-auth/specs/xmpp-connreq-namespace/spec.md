## MODIFIED Requirements

### Requirement: XMPP ConnectionRequest 命名空间
XMPP ConnectionRequest IQ 的命名空间 MUST 为 `urn:broadband-forum-org:cwmp:xmppConnReq-1-0`（带版本后缀 `-1-0`），符合 TR-069 协议规范。`XmlUtil.NS_CWMP_XMPP_CONN_REQ` 常量 MUST 更新为此值。所有引用此常量的代码自动跟随更新。

#### Scenario: IQ 使用版本化命名空间被正确识别
- **WHEN** CPE 收到 IQ child namespace 为 `urn:broadband-forum-org:cwmp:xmppConnReq-1-0` 的 IQ-get
- **THEN** IqHandler MUST 将其识别为 ConnectionRequest 并委托给 ConnReqAuthHandler 处理

#### Scenario: IQ 使用旧版无后缀命名空间不被识别
- **WHEN** CPE 收到 IQ child namespace 为 `urn:broadband-forum-org:cwmp:xmppConnReq`（无 `-1-0`）的 IQ-get
- **THEN** IqHandler MUST NOT 将其识别为 ConnectionRequest，MUST 返回 service-unavailable IQ-error