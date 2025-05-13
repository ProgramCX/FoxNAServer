<p align="center"><img src="./icon.png" width="100" height="100"></p>
<h1 align="center">FoxNAS 服务端</h1>

<div align="center">

[![star](https://img.shields.io/github/stars/ProgramCX/FoxNAServer?logo=github&style=round-square)](https://github.com/ProgramCX/flow_im_app/stargazers)
[![license](https://img.shields.io/github/license/ProgramCX/FoxNAServer?style=round-square&logo=github)](https://github.com/ProgramCX/flow_im_app/blob/main/LICENSE)
[![Activity](https://img.shields.io/github/last-commit/ProgramCX/FoxNAServer?style=round-square&logo=github)](#)
[![Backend](https://img.shields.io/badge/Backend-SpringBoot%202.6.13-brightgreen.svg?style=round-square&logo=spring)](#)

</div>

>

[English](README.md) | 中文

FoxNAS 服务端基于 **JDK 17** 和 **Spring Boot 2.6.13** 开发，提供 **定时广播发现设备**、**实时媒体流解码**、**文件系统管理** 等关键服务。适用于私有服务器、嵌入式设备、家庭 NAS 等场景，与 [FoxNAS 客户端](https://github.com/ProgramCX/FoxNAS) 搭配使用，实现高效、智能的 NAS 解决方案。

## 功能

- **定时广播设备信息**  
  周期性发送 UDP 广播包，在局域网内自动暴露设备信息，客户端无需手动配置 IP 即可发现服务器。

- **实时流解码服务**  
  支持对本地存储的音视频文件（如 MKV、MP4、MP3）等常见媒体文件进行实时解码，并通过网络传输给客户端播放。无需客户端完整下载，即可边解码边播放，提升媒体访问的便捷性与流畅性。

- **文件系统管理**  
  提供远程文件浏览、上传、下载、重命名、删除等操作的 REST API 接口，实现高效的文件管理。


- **动态域名解析（DDNS）**  
  当公网 IP 发生变化时，自动调用指定厂商 API（支持腾讯云、阿里云等）更新域名解析，确保远程访问地址始终有效。

- **邮件通知提醒**  
  在检测到 IP 变动或其他重要事件时，自动发送邮件通知用户，实时掌握设备运行状态。

- **多客户端连接支持**  
  可同时处理多个客户端请求，支持认证机制与权限管理，满足多人/多设备同时访问的需求。

## 技术栈

- **JDK 版本**：17  
- **后端框架**：Spring Boot 2.6.13  
- **主要组件**：Spring Web、Quartz、Spring Security、JavaMail、FFmpeg、MyBatis Plus
- **构建工具**：Maven 3.9.9

## 部署方式

服务端可部署于任何支持 Java 17 的设备：

- 家庭 NAS 服务器  
- 嵌入式 Linux 主机  
- 云服务器实例  
- 本地虚拟机或实体机

并搭配 FoxNAS 客户端进行局域网或远程访问。

## 贡献指南

欢迎社区用户以任何形式参与贡献，包括代码开发、文档编写、问题反馈、功能建议等。您可以通过 Issue 或 PR 加入开发共建。

## 协议

FoxNAS 使用 [GPL 3.0 License](LICENSE) 协议。

---

> 当前服务端项目正在积极开发中，预计将在两个月内发布首个稳定版本。欢迎关注项目进展，提出建议或参与贡献！
