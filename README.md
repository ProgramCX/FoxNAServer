<p align="center"><img src="./icon.png" width="100" height="100"></p>
<h1 align="center">FoxNAS Server</h1>

<div align="center">

[![star](https://img.shields.io/github/stars/ProgramCX/FoxNAServer?logo=github&style=round-square)](https://github.com/ProgramCX/flow_im_app/stargazers)  [![license](https://img.shields.io/github/license/ProgramCX/FoxNAServer?style=round-square&logo=github)](https://github.com/ProgramCX/flow_im_app/blob/main/LICENSE)  [![Activity](https://img.shields.io/github/last-commit/ProgramCX/FoxNAServer?style=round-square&logo=github)](#)  [![Backend](https://img.shields.io/badge/Backend-SpringBoot%202.6.13-brightgreen.svg?style=round-square&logo=spring)](#)

</div>

>

English | [中文](README-zh.md)

FoxNAS Server is developed based on **JDK 17** and **Spring Boot 2.6.13**, providing essential services such as **scheduled device discovery broadcast**, **real-time media stream decoding**, and **file system management**. It is suitable for scenarios like private servers, embedded devices, and home NAS, and works together with the [FoxNAS Client](https://github.com/ProgramCX/FoxNAS) or [FoxNAS Web](https://github.com/ProgramCX/FoxNAS-Web) to provide an efficient and intelligent NAS solution.

## Features

- **Scheduled Device Information Broadcast**  
  Periodically sends UDP broadcast packets to automatically expose device information within the local network, allowing clients to discover the server without manual IP configuration.

- **Real-time Stream Decoding Service**  
  Supports real-time decoding of common media files (e.g., MKV, MP4, MP3) stored locally and transmits them to clients for playback over the network. Clients can stream media without having to download the entire file, enhancing media access convenience and playback smoothness.

- **File System Management**  
  Provides remote file browsing, upload, download, rename, delete, and other operations via REST API, enabling efficient file management.

- **Dynamic Domain Name Resolution (DDNS)**  
  Automatically calls the specified API (supporting Tencent Cloud, Alibaba Cloud, etc.) to update domain name resolution when the public IP changes, ensuring remote access remains functional.

- **Email Notification Alerts**  
  Sends automatic email notifications to users when IP changes or other important events are detected, allowing real-time monitoring of device status.

- **Multiple Client Connections**  
  Can handle multiple client requests simultaneously, supporting authentication mechanisms and permission management for multi-user and multi-device scenarios.

## Tech Stack

- **JDK Version**: 17  
- **Backend Framework**: Spring Boot 2.6.13  
- **Key Components**: Spring Web, Quartz, Spring Security, JavaMail, FFmpeg, MyBatis Plus  
- **Build Tool**: Maven 3.9.9

## Deployment

The server can be deployed on any device that supports Java 17:

- Home NAS servers  
- Embedded Linux hosts  
- Cloud server instances  
- Local virtual machines or physical servers

It can be used with the FoxNAS Client for LAN or remote access.

## Contribution Guide

We welcome community contributions in any form, including code development, documentation writing, bug reporting, and feature suggestions. You can join the development community via Issues or Pull Requests.

## License

FoxNAS is licensed under the [GPL 3.0 License](LICENSE).

---

> The server project is actively under development, with the first stable release expected within two months. Stay tuned for updates, and feel free to provide feedback or contribute!
