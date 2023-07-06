# DeepLX
在DeepLX的基础上增加批处理功能

## 编译
直接运行
```shell
./mvnw compile exec:java
```

编译native image
```shell
./mvnw package -Pnative
```

编译native image的docker镜像
```shell
# 暂无
```

## 接口
单次翻译使用body参数"text", 批量翻译使用body参数"texts"。

如果提供了参数asynId(可选)，后台会合并1秒内asynId相同的请求批量处理，防止封IP，响应内容是一样的
```http request
POST http://127.0.0.1:1188/translate?asynId=immersive
Content-Type: application/json

{
  "text": "specified",
  "source_lang": "en",
  "target_lang": "zh"
}
```
```json
{
  "code": 200,
  "id": 8303473001,
  "data": "特定的",
  "alternatives": [
    "指定的",
    "指定",
    "具体说明"
  ]
}
```

```http request
POST http://127.0.0.1:1188/translate
Content-Type: application/json

{
  "texts": ["specified","application"],
  "source_lang": "en",
  "target_lang": "zh"
}
```
```json
{
  "code": 200,
  "id": 8303473002,
  "datas": [
    {
      "data": "特定的",
      "alternatives": [
        "指定的",
        "指定",
        "具体说明"
      ]
    },
    {
      "data": "应用",
      "alternatives": [
        "申请",
        "申请书",
        "应用范围"
      ]
    }
  ]
}
```
