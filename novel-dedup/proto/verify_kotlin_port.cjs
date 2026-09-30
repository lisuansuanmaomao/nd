/**
 * 把 NameParser.kt 里的正则字符串【原样】搬过来跑一遍，输出 JSON。
 * 目的：在没有 JDK 的机器上，至少验证这些正则语法合法、语义和 Python 版一致。
 */
const fs = require('fs');
const path = require('path');

// ---------------------------------------------------------------- 词表
const TAG_WORDS = new Set([
    "1v1", "np", "双洁", "双处", "sc", "甜文", "虐文", "爽文", "宠文", "甜宠",
    "都市", "高干", "校园", "娱乐圈", "豪门", "总裁", "种田", "修仙", "玄幻",
    "言情", "耽美", "百合", "无cp", "重生", "穿书", "穿越", "快穿", "系统",
    "年下", "年上", "强强", "骨科", "追妻", "火葬场", "破镜重圆", "先婚后爱",
    "青梅竹马", "he", "be", "gb", "bg", "gl", "bl", "abo", "正剧", "轻松",
    "搞笑", "短篇", "中篇", "长篇", "免费", "首发", "独家", "精校", "精校版",
    "全本", "未删减", "慎入", "排雷", "完结", "番外", "补番", "连载", "更新中",
    "清水", "狗血", "沙雕", "无限流", "年代文", "家长里短", "女主", "男主"
]);

const STATUS_WORDS = ["已完结", "完结文", "完结", "全本", "完本", "连载中", "连载", "更新中", "太监"];

const SEP_CHARS = " \t\n_—–·|/\\,，、;；:：~～-";

// 与 Kotlin: "[" + SEP_CHARS.replace("\\", "\\\\") + "]" 等价
const SEP_CLASS = "[" + SEP_CHARS.split("\\").join("\\\\") + "]";
const SEP_TAIL = "(?:" + SEP_CLASS + ")*";

const R = (src, flags) => new RegExp(src, flags || "");

const RE_WS = R("\\s+", "g");
const RE_NUM2 = R("^\\d{1,2}$");
const RE_TOKENS = R("[\\s,、/+&;；]+", "g");
const RE_URL = R("(?:https?://|www\\.)[^\\s\\u4e00-\\u9fff]*", "gi");
const RE_LEAD_BRACKET = R("^\\s*[\\[【]\\s*([^\\[\\]【】]*?)\\s*[\\]】]\\s*");
const RE_CORNER_BRACKET = R("【\\s*([^【】]*?)\\s*】", "g");
const RE_SQUARE_BRACKET = R("\\[\\s*([^\\[\\]]*?)\\s*\\]", "g");
const RE_AUTHOR = R("作者\\s*[:：]?\\s*([^\\s()\\[\\]【】《》（）|,，、;；]+)");
const RE_BOOKNAME = R("《\\s*([^》]+?)\\s*》");
const RE_PAREN_TAIL = R("[\\[【(（]\\s*([^()\\[\\]（）【】]{0,48}?)\\s*[)）\\]】]\\s*$");
const RE_ANY_PAREN = R("[\\[【(（]\\s*([^()\\[\\]（）【】]{0,48}?)\\s*[)）\\]】]", "g");
const RE_STATUS_TAIL = R(SEP_TAIL + "(?:" + STATUS_WORDS.join("|") + ")\\s*$");
const RE_DONE = R("完结|全本|完本|全书完|已完结");
const RE_TAG_ONLY = R("^[\\d\\s,、/+&a-z]*(?:完结|全本|番外|补番)[\\d\\s,、/+&a-z]*$");
const RE_EXTRA = R("(\\d{1,4})\\s*番外", "g");
const RE_AUTHOR_TAIL = R("(著|作品|出品|写)$", "g");
const RE_SEP_RUN = R(SEP_CLASS + "+", "g");

const CHAP_PATTERNS = [
    R("更(?:新)?(?:至|到)?\\s*(\\d{1,6})", "g"),
    R("第\\s*(\\d{1,6})\\s*[章回节]", "g"),
    R("(\\d{1,6})\\s*[章回节]", "g"),
    R("至\\s*(\\d{1,6})\\s*[章回节]", "g")
];

const CHAP_TAIL = [
    R(SEP_TAIL + "(?:更(?:新)?(?:至|到)?|第|至|正文|共|全书)\\s*\\d{1,6}\\s*[章回节]?\\s*$"),
    R(SEP_TAIL + "\\d{1,6}\\s*[章回节]\\s*$"),
    R(SEP_TAIL + "\\d{1,4}\\s*番外\\s*$")
];

// ---------------------------------------------------------------- 工具
function trimSep(s) {
    let a = 0, b = s.length;
    while (a < b && SEP_CHARS.indexOf(s[a]) >= 0) a++;
    while (b > a && SEP_CHARS.indexOf(s[b - 1]) >= 0) b--;
    return s.slice(a, b);
}

function normalize(s) {
    if (!s) return "";
    let t = s.normalize('NFKC');
    t = t.split('（').join('(').split('）').join(')').split('【').join('[').split('】').join(']');
    t = t.replace(RE_WS, "");
    return t.toLowerCase();
}

function isTag(content) {
    let c = content.normalize('NFKC').toLowerCase();
    c = c.split('（').join('(').split('）').join(')');
    c = c.replace(RE_WS, " ").trim();
    if (c.length === 0) return true;
    if (RE_NUM2.test(c)) return true;
    const tokens = c.split(RE_TOKENS).filter(t => t.length > 0);
    if (tokens.length === 0) return true;
    if (tokens.every(t => RE_NUM2.test(t) || TAG_WORDS.has(t))) return true;
    if (tokens.some(t => TAG_WORDS.has(t))) return true;
    if (RE_TAG_ONLY.test(c)) return true;
    return false;
}

function stripTrailingNoise(input, tags) {
    let s = input;
    let changed = true;
    while (changed) {
        changed = false;

        const pm = s.match(RE_PAREN_TAIL);
        if (pm && isTag(pm[1])) {
            tags.push(pm[1]);
            s = trimSep(s.slice(0, pm.index));
            changed = true;
            continue;
        }

        const sm = s.match(RE_STATUS_TAIL);
        if (sm) {
            tags.push(trimSep(sm[0]));
            s = trimSep(s.slice(0, sm.index));
            changed = true;
            continue;
        }

        for (const p of CHAP_TAIL) {
            const m = s.match(p);
            if (m) {
                tags.push(trimSep(m[0]));
                s = trimSep(s.slice(0, m.index));
                changed = true;
                break;
            }
        }
    }
    return s;
}

function keyifyTitle(raw) {
    let s = raw.replace(RE_ANY_PAREN, (m, g1) => (isTag(g1) ? "" : m));
    for (const w of STATUS_WORDS) s = s.split(w).join("");
    s = normalize(s);
    s = s.replace(RE_SEP_RUN, "");
    return s;
}

function parse(fileName) {
    const original = fileName;
    let name = fileName;
    if (name.toLowerCase().endsWith(".txt")) name = name.slice(0, -4);
    name = name.trim();

    name = name.replace(RE_URL, " ");

    const tags = [];

    while (true) {
        const m = name.match(RE_LEAD_BRACKET);
        if (!m || m.index !== 0) break;
        tags.push(m[1]);
        name = name.slice(m[0].length);
    }

    name = name.replace(RE_CORNER_BRACKET, (m, g1) => { tags.push(g1); return " "; });
    name = name.replace(RE_SQUARE_BRACKET, (m, g1) => { tags.push(g1); return " "; });

    let author = "";
    const am = name.match(RE_AUTHOR);
    if (am) {
        author = am[1].trim().replace(RE_AUTHOR_TAIL, "");
        name = name.slice(0, am.index) + " " + name.slice(am.index + am[0].length);
    }

    const bm = name.match(RE_BOOKNAME);
    let titleRaw = bm ? bm[1] : name;
    titleRaw = trimSep(stripTrailingNoise(titleRaw, tags));
    if (titleRaw.length === 0) titleRaw = trimSep(name);

    let chap = 0;
    for (const p of CHAP_PATTERNS) {
        for (const m of original.matchAll(p)) {
            const v = parseInt(m[1], 10);
            if (!isNaN(v) && v > chap) chap = v;
        }
    }

    let extra = 0;
    for (const m of original.matchAll(RE_EXTRA)) {
        const v = parseInt(m[1], 10);
        if (!isNaN(v) && v > extra) extra = v;
    }

    const done = RE_DONE.test(original);

    return {
        title: titleRaw,
        titleKey: keyifyTitle(titleRaw),
        author: author,
        authorKey: normalize(author),
        chap: chap,
        extra: extra,
        done: done
    };
}

// ---------------------------------------------------------------- main
const here = __dirname;
const lines = fs.readFileSync(path.join(here, 'corpus.txt'), 'utf8').split(/\r?\n/);
const out = {};
for (const line of lines) {
    if (!line.trim()) continue;
    out[line] = parse(line);
}
const sorted = {};
for (const k of Object.keys(out).sort()) sorted[k] = out[k];
fs.writeFileSync(path.join(here, 'js_out.json'), JSON.stringify(sorted, null, 1), 'utf8');
console.log('wrote js_out.json:', Object.keys(sorted).length, 'entries');
