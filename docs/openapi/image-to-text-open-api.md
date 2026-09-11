# 图片转文字接口

## 接口说明

该接口用于上传一张图片，并识别图片中的文字。

### 生产环境

推荐调用地址：

```text
POST https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/ocr/image-to-text
```

兼容调用地址：

```text
POST https://tkassetplant.fnn.net.cn/tk/open/v1/ocr/image-to-text
```

两个地址执行相同的 OCR 处理逻辑，建议优先使用 `/admin-api/tk/open/v1/ocr/image-to-text`。

## 鉴权说明

该接口不参与用户登录鉴权，调用方直接上传图片即可，不需要携带以下信息：

- 登录 Token
- `Authorization`
- `clientId`
- `clientSecret`
- 请求签名
- 租户参数

当前接口也不要求调用方提供签名、限流标识或配额参数。

除 `multipart/form-data` 请求本身需要的 `Content-Type` 外，不需要手动设置其他请求头。浏览器调用时不要手动设置 `Content-Type`，由浏览器自动生成 multipart boundary。

## 请求信息

| 项目 | 内容 |
| --- | --- |
| 请求方法 | `POST` |
| 请求类型 | `multipart/form-data` |
| 文件字段 | `file` |
| 单次处理数量 | 1 张图片 |
| 最大文件大小 | `20MB` |
| 支持格式 | `JPG`、`JPEG`、`PNG`、`WEBP` |

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | File | 是 | 待读取文字的图片 |

请求体不是 JSON，必须使用 `multipart/form-data` 上传文件。

## curl 调用

```bash
curl -X POST "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/ocr/image-to-text" \
  -F "file=@/path/to/image.png"
```

## JavaScript 调用

```javascript
const formData = new FormData();
formData.append('file', imageFile);

const response = await fetch(
  'https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/ocr/image-to-text',
  {
    method: 'POST',
    body: formData
  }
);

const result = await response.json();
if (result.code !== 0) {
  throw new Error(result.msg || '图片文字识别失败');
}

console.log(result.data.text);
```

## 成功响应

成功时返回项目统一的 `CommonResult` 结构，`code` 为 `0`，识别文字位于 `data.text`：

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "text": "图片中识别出的文字"
  }
}
```

调用方应以响应中的 `code` 判断业务是否成功，不要只根据 `msg` 判断。

## 失败响应

### 图片为空

```json
{
  "code": 400,
  "msg": "图片文件不能为空",
  "data": null
}
```

### 图片格式不支持

```json
{
  "code": 400,
  "msg": "仅支持 JPG、PNG、WEBP 图片",
  "data": null
}
```

### 图片超过大小限制

```json
{
  "code": 400,
  "msg": "图片文件不能超过 20MB",
  "data": null
}
```

### OCR 模型服务不可用

```json
{
  "code": 106000052,
  "msg": "图片文字识别服务暂不可用，请稍后重试",
  "data": null
}
```

常见错误码：

| `code` | 含义 | 处理建议 |
| --- | --- | --- |
| `0` | 请求成功 | 从 `data.text` 读取识别结果 |
| `400` | 请求参数不正确 | 检查是否上传 `file`、文件格式和文件大小 |
| `106000052` | OCR 模型服务暂不可用 | 稍后重试 |
| `500` | 系统异常 | 稍后重试，并联系接口提供方排查 |

错误响应中的 `data` 为 `null`。错误信息以实际返回的 `msg` 为准。

## 数据处理说明

- 当前接口一次请求只处理一张图片。
- 接口业务逻辑不会保存上传的图片。
- 接口业务逻辑不会保存 OCR 历史结果。
- 图片仅在本次请求处理期间发送给系统配置的视觉模型服务进行识别。
- 调用方请勿上传不应提交给第三方模型服务处理的敏感图片。

## 调用注意事项

- 文件字段名称必须是 `file`，不能改成其他名称。
- 浏览器调用时不要手动设置 `Content-Type`。
- 图片大小必须不超过 `20MB`。
- 识别失败时请读取 `code` 和 `msg`，不要直接读取 `data.text`。
