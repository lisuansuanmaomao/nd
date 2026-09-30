# 小说TXT去重（Android APK）

手机上同一本小说攒了一堆 txt —— 更到 10 章一个、30 章又一个、完结再来一个。
这个 App 就是干一件事：**在一个大文件夹（里面可以有很多层小文件夹）里，把同一本书的旧版本找出来，只留最新的那一份。**

**只读文件名，不打开 txt 内容。** 因为书名、作者、更新程度你都写在文件名里了。

---

## 一、保留 / 移走规则

| # | 情况 | 处理 |
|---|------|------|
| 1 | 组内**有**「完结」版 | 完结版里留**体积最大**的，其余（含连载版）全部移走 |
| 2 | 组内**没有**完结版 | 留**章数最大**的；章数相同则留体积大的 |
| 3 | 有文件读不出章数 | **不自动动**，标成「待确认」交给你看 |
| 4 | 同名书有 ≥2 个不同作者，而某些文件没写作者 | 这些没写作者的**整组不动**（无法判断归属） |
| 5 | 组内只有 1 个文件 | 不动 |
| 6 | 同名书全都没写作者 | 按书名正常去重（没有冲突来源） |

分组键是 **书名 + 作者**。书名会做归一化再比对：
全角转半角、去空白、统一括号、忽略大小写，并剥掉括号里的标签（`1v1` `np` `甜文` `完结` `精校版` …）和副本序号 `(1)` `(2)`。

---

## 二、安全设计（重要）

这个 App **不会删你的文件**。

- 「扫描」只是预览，一个字节都不动。
- 「执行整理」是把旧版本**移动**到 `你选的文件夹/_重复待删/` 下面。
- 目标里**原样保留原来的目录结构**，所以不会有重名冲突。
- 同时生成 `_重复待删/_整理记录.txt`，逐行记录「原路径 → 新路径」，照它就能全部移回去。
- 确认新版本没问题后，你自己把整个 `_重复待删` 文件夹删掉即可。
- 列表里每一组都能单独取消勾选；拿不准的组默认不勾。

---

## 三、支持的文件名写法（已实测）

| 文件名 | 解析出的书名 | 作者 | 章数 | 番外 | 完结 |
|---|---|---|---|---|---|
| `[1v1 更235]《梨汁软糖（甜文）》作者：abc.txt` | 梨汁软糖 | abc | 235 | | |
| `霸道少爷爱上我 （1v1） (完结).txt` | 霸道少爷爱上我 | — | | | ✅ |
| `[完结]春山寒 作者：江入玦明.txt` | 春山寒 | 江入玦明 | | | ✅ |
| `[完结+16番外]《罪爱（np 都市 高干）》作者：九铃【补番】.txt` | 罪爱 | 九铃 | | 16 | ✅ |
| `盗墓笔记 更500 (1).txt` | 盗墓笔记 | — | 500 | | |
| `《盗墓笔记》作者：南派三叔 更1230.txt` | 盗墓笔记 | 南派三叔 | 1230 | | |
| `www.99lib.net_斗破苍穹 更1600.txt` | 斗破苍穹 | — | 1600 | | |
| `斗破苍穹 更新至1600章 作者：天蚕土豆.txt` | 斗破苍穹 | 天蚕土豆 | 1600 | | |
| `斗破苍穹【全本】作者：天蚕土豆.txt` | 斗破苍穹 | 天蚕土豆 | | | ✅ |
| `《1984》作者：奥威尔 更12章.txt` | 1984 | 奥威尔 | 12 | | |

识别的章数写法：`更235` / `更新235` / `更新至235章` / `第235章` / `235章` / `至235章` / `共235章`。
识别的完结写法：`完结` `全本` `完本` `全书完` `已完结` `完结+16番外`。
书名外挂的标签：`[...]` `【...】` `(...)` `（...）`，只要内容是标签词就会被忽略。

---

## 四、怎么拿到 APK（不用装 Android Studio）

我这边这台机器没有 JDK / Android SDK，也连不上外网，所以**没法直接帮你编译**。
工程里已经配好了 GitHub Actions，推到 GitHub 上云端免费编译，你下载 APK 就行。

### 步骤

1. **注册 / 登录 GitHub**（没有账号先注册一个）。

2. **新建仓库**：右上角 `+` → `New repository`，名字随便填（比如 `novel-dedup`），
   选 `Public` 或 `Private` 都行，然后 `Create repository`。

3. **上传代码**。在空仓库页面点 `uploading an existing file`，
   把 `novel-dedup` 文件夹里的**所有内容**（包括 `app`、`build.gradle.kts`、`settings.gradle.kts`、
   `gradle.properties`、`README.md`）拖进去，点 `Commit changes`。

4. **补上工作流文件**（网页上传经常会漏掉 `.github` 这种点开头的文件夹，所以要手动建一次）：
   - 仓库页点 `Add file` → `Create new file`
   - 文件名框里输入：`.github/workflows/build-apk.yml`（输入 `/` 会自动建目录）
   - 把下面内容粘进去，点 `Commit changes`：

   ```yaml
   name: 编译 APK
   on:
     push:
       branches: [ main, master ]
     workflow_dispatch:
   jobs:
     build:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with:
             distribution: temurin
             java-version: '17'
         - uses: gradle/actions/setup-gradle@v4
           with:
             gradle-version: '8.7'
         - run: gradle assembleDebug --no-daemon --stacktrace
         - uses: actions/upload-artifact@v4
           with:
             name: novel-dedup-apk
             path: app/build/outputs/apk/debug/*.apk
             if-no-files-found: error
   ```

   > 如果你会用 git，直接 `git clone` 仓库、把整个 `novel-dedup` 目录拷进去、
   > `git add -A && git commit -m init && git push`，`.github` 就一起上去了，第 4 步可跳过。

5. **等编译**：打开仓库的 `Actions` 标签页，能看到一条正在跑的 `编译 APK`。
   第一次大约 3～5 分钟（要下载 Gradle 和依赖）。

6. **下载 APK**：点进那条运行记录，页面底部 `Artifacts` 里点 `novel-dedup-apk`，
   下到的是一个 zip，解压出来就是 `app-debug.apk`。

7. **装到手机**：把 apk 传到手机（微信/QQ/数据线都行），点它安装。
   系统会提示「未知来源应用」，允许一次即可。

---

## 五、手机上怎么用

1. 第一次打开，点顶部按钮 → 系统设置里给这个 App 打开 **「所有文件访问权限」**
   （Android 11+ 必须给，否则扫不到也改不了文件；Android 10 及以下会直接要存储权限）。
2. 回到 App，点 **「选择小说文件夹」**，逐层点进去，选中你放小说的大文件夹（比如 `内部存储/小说`），
   点 **「就选这个文件夹」**。
3. 点 **「开始扫描」**。扫完后列表里会列出所有**有重复的**书，每组显示：
   - `保留：xxx.txt  12.34 MB  完结`
   - `移走 2 个：…`
   - `未处理（需你自己看一眼）…`
4. **逐条核对**。不想动的那组，把左边的勾取消。
5. 点 **「执行整理」** → 确认 → 完成后旧版本都在 `_重复待删` 里了。
6. 用几天确认新版本都正常，再删掉 `_重复待删` 文件夹。

> 建议第一次先拿一个**小文件夹**（放十几本书）试一遍，确认规则符合你的预期，再拿全库跑。

---

## 六、已知的保守之处

程序设计上偏向「宁可不动，也不误删」，所以有几种情况它**不会**帮你合并：

- **书名括号里是正文内容而非标签**：`《红楼梦（上下册）》` 和 `《红楼梦》` 会被当成两本书。
  （`（上下册）` 不在标签词表里，所以保留在书名中。）
- **同名书有多个作者，且某个文件没写作者**：那个文件不会被合并，标为待确认。
- **正文里没有章数也没有完结标记的文件**：不自动动。

这些"不动"都会在界面上明确标出来，不会悄悄跳过。

想放宽或收紧，改 `app/src/main/java/com/noveldedup/NameParser.kt` 顶部的
`TAG_WORDS`（标签词表）和 `STATUS_WORDS`（状态词表）即可。

---

## 七、工程结构

```
novel-dedup/
├─ .github/workflows/build-apk.yml   GitHub 云端打包
├─ build.gradle.kts / settings.gradle.kts / gradle.properties
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/noveldedup/
│     │  ├─ MainActivity.kt        界面 + 权限 + 流程编排
│     │  ├─ FolderPickerDialog.kt  自建目录选择器
│     │  ├─ Scanner.kt             递归扫描 .txt
│     │  ├─ NameParser.kt          ★ 文件名解析（核心规则）
│     │  ├─ Grouper.kt             ★ 分组
│     │  ├─ Planner.kt             ★ 留谁/移谁
│     │  ├─ Mover.kt               移动到 _重复待删
│     │  ├─ GroupAdapter.kt        结果列表
│     │  └─ Model.kt
│     └─ res/                      布局、主题、矢量图标（无二进制资源）
└─ proto/                          PC 上的规则验证脚本（不参与打包）
   ├─ parser_proto.py              NameParser.kt 的 Python 原型
   ├─ grouper_proto.py             Grouper/Planner 的 Python 原型
   ├─ run_tests.py                 15 条断言
   ├─ corpus.txt                   28 个仿真文件名
   ├─ dump_py.py / verify_kotlin_port.cjs / compare.py
   └─ check_bindings.py            布局与绑定字段静态检查
```

---

## 八、验证记录

没有 Android SDK 也能验证的部分，都已经跑过了：

- `proto/run_tests.py` —— 20 个仿真文件、9 组，**15 条断言全部通过**
  （完结优先、体积最大、章数最大、同名不同作者不互相删、无作者安全归并、独苗不动、
  书名里的数字不被当章数）。
- `proto/verify_kotlin_port.cjs` —— 把 `NameParser.kt` 里的正则字符串**原样**搬到 Node 上执行，
  与 Python 版逐字段对比：**28 个文件名 × 7 个字段 = 196 项，0 处不一致**。
- `proto/check_bindings.py` —— 布局 id、Kotlin ViewBinding 字段、清单资源引用，**全部通过**。

也就是说：**规则逻辑和资源引用都验证过了；剩下没验证的只有 Kotlin 编译和 Gradle 打包本身**
（这一步只能等 GitHub Actions 跑）。

重新跑一遍：

```powershell
cd proto
python run_tests.py
python dump_py.py; node verify_kotlin_port.cjs; python compare.py
python check_bindings.py
```

---

## 九、想改点什么

| 想改的东西 | 改哪里 |
|---|---|
| 认不出的标签词（比如 `（上下册）` 想当标签） | `NameParser.kt` 的 `TAG_WORDS` |
| 认不出的章节/完结写法 | `NameParser.kt` 的 `CHAP_PATTERNS`、`CHAP_TAIL`、`RE_DONE` |
| 取舍规则（比如"完结里不按体积，按章数"） | `Planner.kt` 的 `planOne()` |
| 回收站文件夹名字 | `Scanner.kt` 的 `QUARANTINE` |
| 改成直接删除而不是移动 | `Mover.kt` 的 `execute()`（**不建议**） |

改完请同步改 `proto/` 里对应的 Python 原型并重跑测试。
