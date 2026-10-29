# TVBoxMobile

基于
* [q215613905](https://github.com/q215613905)/[TVBoxOS](https://github.com/q215613905/TVBoxOS)   

## Build
[Github Actions](https://github.com/XiaoRanLiu3119/MBox-Build/actions)   

精力有限,未必会及时维护,仅用于学习
   
## 推荐使用   
[takagen99](https://github.com/takagen99/Box)   
[FongMi](https://github.com/FongMi/TV)

## 弹幕服务

手机版已适配播放器弹幕，不新增或改动播放器界面。弹幕来源按以下顺序生效：

1. `playerContent` 返回的 `danmaku` 字段（可直接返回 XML，也可返回 XML 地址）。
2. 接口配置顶层的 `danmaku` 字段。
3. 未配置时使用兼容 Logvar 的内置弹幕服务。

自定义 `danmaku` 支持三种形式：Logvar 兼容服务根地址、带 `{name}` / `{episode}` 占位符的搜索地址，以及以 `/danmaku` 结尾的 POST 接口。配置为空的自动搜索仍可通过本地设置项 `danmu_open` 关闭。
