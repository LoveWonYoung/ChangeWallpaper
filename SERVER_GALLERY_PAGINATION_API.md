# 网络壁纸图库分页接口约定

当服务端图库达到数千张图片时，不能再通过 `/info` 一次性返回全部文件名。服务端应提供真正的分页接口，让客户端每次只获取并显示当前页的数据。

## 1. 图库分页接口

### 请求

```http
GET /gallery?offset=0&limit=24
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `offset` | Integer | `0` | 从第几条记录开始，必须大于或等于 `0` |
| `limit` | Integer | `24` | 本次最多返回多少条，建议限制在 `1`～`100` |

### 成功响应

```json
{
  "total": 7820,
  "offset": 0,
  "limit": 24,
  "has_more": true,
  "wallpapers": [
    "001.jpg",
    "002.webp",
    "003.png"
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `total` | Integer | 当前图库中的壁纸总数 |
| `offset` | Integer | 本次请求使用的起始位置 |
| `limit` | Integer | 本次请求使用的最大返回数量 |
| `has_more` | Boolean | 是否还有下一页 |
| `wallpapers` | String[] | 当前页的壁纸文件名，不能包含其他页面的数据 |

第二页请求示例：

```http
GET /gallery?offset=24&limit=24
```

最后一页必须返回：

```json
{
  "total": 7820,
  "offset": 7800,
  "limit": 24,
  "has_more": false,
  "wallpapers": [
    "最后一页的文件名.jpg"
  ]
}
```

当 `offset` 大于或等于 `total` 时，应正常返回空数组：

```json
{
  "total": 7820,
  "offset": 8000,
  "limit": 24,
  "has_more": false,
  "wallpapers": []
}
```

## 2. 精简 `/info`

`/info` 只返回服务状态和图库统计信息，不再携带完整的 `wallpapers` 数组。

### 请求

```http
GET /info
```

### 响应

```json
{
  "wallpaper_count": 7820,
  "supported_formats": [
    "jpg",
    "jpeg",
    "png",
    "webp"
  ]
}
```

如果需要兼容旧版本客户端，可以暂时保留旧版 `/info`，但新版客户端将只通过 `/gallery` 获取文件名。

## 3. 现有图片接口

以下接口可以保持不变：

```http
GET /thumb/{encoded_file_name}
GET /image/{encoded_file_name}
GET /current
GET /next
GET /random
GET /health
```

接口用途：

- `/thumb/{encoded_file_name}`：返回列表使用的压缩缩略图，建议宽度控制在 400～600px。
- `/image/{encoded_file_name}`：返回原图，用于全屏预览和设置壁纸。
- `/current`：返回当前同步壁纸。
- `/next`：返回独立轮换模式的下一张壁纸。
- `/random`：返回随机壁纸。
- `/health`：返回服务健康状态。

建议图片响应支持 `ETag` 或 `Last-Modified`，方便客户端和代理服务器缓存。

## 4. 排序要求

分页前必须使用稳定的排序规则，否则用户翻页时可能遇到重复或遗漏。

推荐选择一种固定规则：

- 按文件名升序；或
- 按修改时间倒序，文件名作为第二排序条件。

示例：

```text
全部文件名 = 扫描图片目录
全部文件名 = 按固定规则排序
total = 全部文件名数量
当前页 = 全部文件名[offset : offset + limit]
has_more = offset + 当前页数量 < total
```

不要先截取数据再排序。

## 5. 参数校验和错误响应

参数不合法时返回 HTTP `400`：

```json
{
  "error": "invalid pagination parameters"
}
```

建议状态码：

| 状态码 | 使用场景 |
| --- | --- |
| `200` | 请求成功，包括超出范围后返回空数组 |
| `400` | `offset` 或 `limit` 不合法 |
| `404` | 指定图片不存在 |
| `500` | 服务端扫描目录、读取文件或生成缩略图失败 |

## 6. 文件名安全

客户端会对路径中的文件名进行 URL 编码，服务端需要正确解码。同时必须防止路径穿越：

- 拒绝包含 `..` 的非法路径。
- 不允许读取壁纸目录之外的文件。
- 最终解析出的文件路径必须确认仍位于配置的壁纸根目录内。
- 只允许服务端配置的图片扩展名。

## 7. 性能建议

- 不要在每个分页请求中重复遍历并读取所有图片内容，只需要获取文件名和必要的文件属性。
- 图片目录变化不频繁时，可以缓存排序后的文件列表，并在目录发生变化后刷新。
- `/gallery` 只返回文件名，不需要为每张图片返回 Base64、尺寸或原图数据。
- 对分页 JSON 设置合理的缓存策略；图库变化时更新 `ETag`。
- 缩略图应提前生成或按需生成后缓存，避免每次请求都重新压缩原图。

## 8. 客户端接入条件

服务端完成后，需要向客户端确认：

1. `/gallery` 的正式访问地址。
2. 实际成功响应 JSON。
3. 默认和最大 `limit`。
4. 图库采用的排序规则。
5. 是否继续保留旧版 `/info` 中的 `wallpapers` 字段。

客户端随后会改为：首次请求 `offset=0&limit=24`，点击下一页时请求新的 `offset`，不再下载和保存全部文件名清单。
