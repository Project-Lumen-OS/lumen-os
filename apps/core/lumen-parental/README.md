# lumen-parental

The stock parent page of XGIMI Home (家长设置, `com.xgimi.childmode`, XgimiHome_release838 on the Z9X dump) rebuilt
for Lumen, one module for every model that has the row:

| stock (XGIMI Home child mode) | Lumen |
|---|---|
| 儿童锁 child lock switch, closing it drops every control | `ParentalState.lockOn` |
| 解锁方式 方向键密码 remote arrow key password (4 keys) | `PinCode`, `PinActivity` |
| 手机安全密码 phone password over the XGIMI app (cloud) | not offered (no XGIMI cloud) |
| 找回密码 / 答题解锁 forgotten password, answer a question | `Quiz` (multiple choice) |
| 管控范围 仅儿童模式 / 全局 control range | `ControlRange` |
| 单次使用时长, rest 10 minutes after it | `sessionMin`, `ParentalRules.REST_MIN` |
| 全天使用时长 daily time | `dailyMin` |
| 开机需要密码验证 password at power on | `bootPin` |
| 开机后自动进入儿童模式 child mode at power on | `bootChild` |
| child tab of XGIMI Home (cloud kids channel) | child mode: Home shows only the apps the parent picked |
| 护眼 rows (eye mode, anti eye shot, timed rest) | hint to the projector's own eye protection (stock: "can be changed in Settings") |
| 宝贝资料, 个性化推荐, 禁看内容 (cloud) | not offered |

Single session choices are the stock list (`SettingActivity.timeList`: off, 20, 30, 40, 50, 60 min). The daily list was
not found in the decompile (cloud config): 1, 1.5, 2, 3, 4 h, marked for the owner.

Storage: Home's device protected preferences (`lumen_parental`), the password only as a salted SHA-256.
Tests: `bash test/run.sh` (host, plain Java).
