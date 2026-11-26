#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Targeted mutants of the weather city search: each must make test/weather/run.sh fail
(python3 apps/LumenHome/test/weather/mutate.py [NAME...])."""
import os
import shutil
import subprocess
import sys
import tempfile

APP = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CS = "src/org/lumen/home/weather/CitySearch.java"
W = "src/org/lumen/home/weather/Weather.java"

MUTANTS = {
    "no_own_language_query": (CS, "if (own != null && !own.equals(ui) && !isCyrillicLanguage(ui)) out.add(new String[]{n, own});", ""),
    "own_query_always": (CS, "if (own != null && !own.equals(ui) && !isCyrillicLanguage(ui))", "if (own != null)"),
    "short_allowed": (CS, "        if (n.length() < 2) return out;\n", "\n"),
    "no_trim": (CS, '        String n = q == null ? "" : q.trim();\n        if (n.length() < 2) return out;', '        String n = q == null ? "" : q;\n        if (n.length() < 2) return out;'),
    "ui_default": (CS, 'String ui = uiLang == null || uiLang.isEmpty() ? "en" : uiLang;', 'String ui = uiLang == null ? "en" : uiLang;'),
    "kk_letters": (CS, 'if (l.matches(".*[әңғүұқөһ].*")) return "kk";', ""),
    "be_letter": (CS, "if (l.indexOf('ў') >= 0) return \"be\";", ""),
    "translit_cyrillic_too": (CS, "if (n.length() < 2 || !hasLatin(n) || hasCyrillic(n)) return null;", "if (n.length() < 2 || !hasLatin(n)) return null;"),
    "translit_digits": (CS, "if (n.length() < 2 || !hasLatin(n) || hasCyrillic(n)) return null;", "if (n.length() < 2 || hasCyrillic(n)) return null;"),
    "translit_lang_ui": (CS, 'isCyrillicLanguage(uiLang) ? uiLang : "ru"', "uiLang"),
    "needs_more_zero": (CS, "if (p != null && p > 0) return false;", "if (p != null && p >= 0) return false;"),
    "merge_no_dedupe": (CS, "seen.putIfAbsent(id.applyAsLong(t), t);", "seen.put(id.applyAsLong(t) * 1000 + seen.size(), t);"),
    "merge_no_sort": (CS, "        out.sort((a, b) -> Long.compare(population.applyAsLong(b), population.applyAsLong(a)));\n", "\n"),
    "merge_no_cut": (CS, "return out.size() > count ? new ArrayList<>(out.subList(0, count)) : out;", "return out;"),
    "tsk_gone": (CS, '{"tsk", "тск"}, ', ""),
    "zh_gone": (CS, '{"zh", "ж"}, ', ""),
    "iy_gone": (CS, '{"iy", "ий"}, ', ""),
    "soft_sign_gone": (CS, "case '\\'': case '\\u2019': case '\\u02bc': return \"ь\";", ""),
    "y_always_y": (CS, 'return wordStart || nextVowel ? "й" : "ы";', 'return "ы";'),
    "w_search_one_lang": (W, "for (String[] s : CitySearch.queries(q, lang)) {", "for (String[] s : CitySearch.queries(q, lang).subList(0, 1)) {"),
    "w_no_translit": (W, "if (more != null) found.add(searchOne(more[0], more[1], count));", ""),
    "w_no_merge": (W, "return CitySearch.merge(found, c -> c.id, c -> c.population, count);", "return found.get(0);"),
    "w_no_population": (W, 'ct.population = r.optLong("population", 0);', "ct.population = 0;"),
    "w_units_ip": (W, 'String cc = manual != null ? manual.optString("cc") : sp(c).getString("cc", "");', 'String cc = sp(c).getString("cc", "");'),
    "w_city_no_cc": (W, '                    .put("cc", ct.cc).toString());', "                    .toString());"),
}


def run(root):
    r = subprocess.run(["sh", os.path.join(root, "test", "weather", "run.sh")], capture_output=True, text=True, timeout=300)
    return r.returncode


def tree(dst):
    for rel in ("src/org/lumen/home/weather", "test/weather"):
        shutil.copytree(os.path.join(APP, rel), os.path.join(dst, rel))


def main(names):
    tmp = tempfile.mkdtemp(prefix="weather-mut.")
    base = os.path.join(tmp, "base")
    tree(base)
    if run(base) != 0:
        print("FAIL unmutated tree does not pass")
        return 2
    killed = alive = broken = 0
    for name in names or sorted(MUTANTS):
        f, old, new = MUTANTS[name]
        d = os.path.join(tmp, name)
        shutil.copytree(base, d)
        p = os.path.join(d, f)
        text = open(p, encoding="utf-8").read()
        if text.count(old) != 1:
            print("BROKEN %s: anchor found %d times" % (name, text.count(old)))
            broken += 1
            continue
        open(p, "w", encoding="utf-8").write(text.replace(old, new))
        if run(d) != 0:
            killed += 1
        else:
            alive += 1
            print("ALIVE", name)
        shutil.rmtree(d, ignore_errors=True)
    shutil.rmtree(tmp, ignore_errors=True)
    print("mutate: %d killed, %d alive, %d broken of %d" % (killed, alive, broken, killed + alive + broken))
    return 0 if alive == 0 and broken == 0 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
