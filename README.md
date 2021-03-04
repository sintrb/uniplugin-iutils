# uniplugin-iutils
uni-app常用插件封装

```javascript
let itu = uni.requireNativePlugin("IUtilsModule")

// 获取USB设备列表
itu.getUsbDevices({}, res => {
	console.log(JSON.stringify(msg))
});

// 根据厂家ID和产品ID获取USB设备
itu.getUsbDevice({vendorId: 0x154f,productId: 0x1300}, res => {
	console.log(JSON.stringify(msg))
});

// 根据厂家ID、产品ID、端点号传送数据
itu.usbBulkTransfer({
	vendorId: 0x154f,
	productId: 0x1300,
	endpoint: 0x01,
	data: 'SGVsbG9Xb3JsZA==',	// 需要发送的数据base64编码
	timeout: 3000,
}, res => {
	console.log(JSON.stringify(msg))
});

// 根据设备ID、端点号传送数据
itu.usbBulkTransfer({
	deviceId: 1,
	endpoint: 0x01,
	data: 'SGVsbG9Xb3JsZA==',	// 需要发送的数据base64编码
	timeout: 3000,
}, res => {
	console.log(JSON.stringify(msg))
});

```