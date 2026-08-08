/**
 * infra 模块保留 TK 和系统管理需要的基础能力：参数配置、文件上传、API 日志。
 *
 * 1. Controller URL：以 /infra/ 开头，避免和其它 Module 冲突
 * 2. DataObject 表名：以 infra_ 开头，方便在数据库中区分
 */
package cn.iocoder.yudao.module.infra;
