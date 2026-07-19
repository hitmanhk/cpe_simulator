package com.cpe.simulator.desktop.ui;

import java.util.List;

/**
 * TR-069 CWMP fault codes (SOAP Fault detail.Fault.FaultCode), covering the
 * standard 9000-series plus the simulator-specific behavior of each code.
 */
final class ProtocolErrorCodes {

    record ErrorCode(String code, String name, String meaning, String simulatorBehavior) {
    }

    private ProtocolErrorCodes() {
    }

    static List<ErrorCode> all() {
        return List.of(
                new ErrorCode("9000", "Method not supported（方法不支持）",
                        "ACS 请求了设备未实现的 RPC 方法。",
                        "会产生：RpcRegistry 中找不到 handler 时返回。"),
                new ErrorCode("9001", "Request denied（请求被拒绝）",
                        "设备因自身策略拒绝执行（标准未限定具体场景，常见于设备忙/状态不允许）。",
                        "不会产生。"),
                new ErrorCode("9002", "Internal error（内部错误）",
                        "设备处理 RPC 时发生未预期异常。",
                        "会产生：RPC handler 抛出异常时返回（并记录日志）。"),
                new ErrorCode("9003", "Invalid arguments（调用参数无效）",
                        "RPC 调用级参数非法（非参数树层面的值错误）。",
                        "不会产生。"),
                new ErrorCode("9004", "Resources exceeded（资源超限）",
                        "请求超出设备处理能力（如 ParameterNames 数组过大）。",
                        "不会产生。"),
                new ErrorCode("9005", "Invalid parameter name（参数名无效）",
                        "参数路径不存在或寻址非法。",
                        "会产生：GetParameterValues / SetParameterValues / GetParameterNames / "
                                + "GetParameterAttributes / SetParameterAttributes / AddObject / DeleteObject 路径无效时。"),
                new ErrorCode("9006", "Invalid parameter type（参数类型不匹配）",
                        "SetParameterValues 中声明的类型与参数实际类型不符。",
                        "不会产生（类型错误统一按 9007 返回）。"),
                new ErrorCode("9007", "Invalid parameter value（参数值非法）",
                        "值无法按参数类型解析或超出取值范围（int 溢出、boolean/dateTime 格式错误等）。",
                        "会产生：SetParameterValues 值校验失败时。"),
                new ErrorCode("9008", "Non-writable parameter（参数只读）",
                        "尝试写入 writable=false 的参数。",
                        "会产生：SetParameterValues 写只读参数时。"),
                new ErrorCode("9009", "Notification request rejected（通知请求被拒绝）",
                        "设备拒绝为该参数设置 Notification 属性。",
                        "不会产生。"),
                new ErrorCode("9010", "Download failure（下载失败）",
                        "TransferComplete 中上报的通用下载失败。",
                        "会产生：Download 任务按 (1 − download-success-rate) 概率随机失败时。"),
                new ErrorCode("9011", "File transfer server authentication failure（文件服务器认证失败）",
                        "访问文件服务器时凭据被拒绝。",
                        "不会产生。"),
                new ErrorCode("9012", "Unsupported protocol for file transfer（传输协议不支持）",
                        "文件 URL 使用了设备不支持的协议（如设备仅支持 HTTP/FTP）。",
                        "不会产生。"),
                new ErrorCode("9013", "Download failure: unable to join multicast group（无法加入组播组）",
                        "组播下载场景加入组播组失败。",
                        "不会产生。"),
                new ErrorCode("9014", "Download failure: unable to contact file server（无法联系文件服务器）",
                        "文件服务器不可达（DNS/连接失败）。",
                        "不会产生。"),
                new ErrorCode("9015", "Download failure: unable to access file（无法访问文件）",
                        "服务器可达但文件不存在或无权限。",
                        "不会产生。"),
                new ErrorCode("9016", "Download failure: unable to complete download（下载未完成）",
                        "传输中断，未能完整下载。",
                        "不会产生。"),
                new ErrorCode("9017", "Download failure: file corrupted（文件损坏）",
                        "下载完成但内容校验不一致。",
                        "不会产生。"),
                new ErrorCode("9018", "Download failure: file authentication failure（文件验签失败）",
                        "固件签名校验失败。",
                        "不会产生。"),
                new ErrorCode("9019", "Download failure: download timeout（下载超时）",
                        "规定时间内未完成下载。",
                        "不会产生。"),
                new ErrorCode("9020+", "Vendor specific（厂商自定义）",
                        "9020 及以上由厂商自定义，需查阅对应设备手册。",
                        "不会产生。"));
    }
}
