#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""naming.py (release titles and tags) and models_table.py (the README / install guide tables): python3 naming_test.py"""
import os
import subprocess
import sys
import unittest

H = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(H))
import naming  # noqa: E402
import models_table  # noqa: E402

G = naming.G


def rel(tag, pre=True):
    return {"tag_name": tag, "prerelease": pre, "draft": False}


def model(code, install=False, way="trial"):
    return {"model": code, "stock": [{"version": "v1", "install": install}], "builds": [{"way": way}]}


class Naming(unittest.TestCase):
    def test_titles(self):
        self.assertEqual(naming.title("horse_popular", "1.1.0-beta4"), "Lumen OS 1.1.0-beta4 \u00b7 Z9X (G0082)")
        self.assertEqual(naming.title("goat_elegant", "1.0.10-beta24"),
                         "Lumen OS 1.0.10-beta24 \u00b7 Z9X HB (G0130, G0148)")
        self.assertEqual(naming.title("dragon_healthy", "1.0.10"), "Lumen OS 1.0.10 \u00b7 Play 6 (G0058)")
        self.assertEqual(naming.title("h0063", "1.0.10-beta7"),
                         "Lumen OS 1.0.10-beta7 \u00b7 TITAN Noir Max (H0063) \u00b7 Live")
        # the code in the registry name is not repeated
        self.assertEqual(naming.title("g0114", "1.0.10-beta1"),
                         "Lumen OS 1.0.10-beta1 \u00b7 RS30 Ultra (G0114) \u00b7 Live")
        self.assertEqual(naming.channel_title(["g0111", "g0100"]),
                         "Lumen OS updates \u00b7 X50 Ultra (G0111), X50 Ultra Max (G0100)")

    def test_tags_are_the_catalog_patterns(self):
        self.assertEqual(naming.tag("goat_elegant", "1.0.10-beta24"), "z9x-hb-1.0.10-beta24")
        self.assertEqual(naming.tag("g0155", "1.0.10-beta9"), "rs30-1.0.10-beta9")
        with self.assertRaises(SystemExit):
            naming.tag("g0111", "1.0.10 beta")
        for c in naming.devices.load(G):
            if os.path.exists(os.path.join(G, "devices", c, "catalog.conf")):
                self.assertEqual(naming.of_tag(naming.tag(c, "1.0.10-beta3")), (c, "1.0.10-beta3"), c)
        # longest slug wins, channel tags and old v-tags belong to no model
        self.assertEqual(naming.of_tag("rs30ultra-1.0.10-beta4"), ("g0170", "1.0.10-beta4"))
        self.assertIsNone(naming.of_tag("z9x-hb"))
        self.assertIsNone(naming.of_tag("v1.0.7"))

    def test_new_model_slug(self):
        taken = naming.taken_slugs()
        self.assertEqual(naming.slug("XGIMI MIRA 4K Pro", "G0110", set()), "mira4kpro")
        self.assertEqual(naming.slug("XGIMI RS30 Ultra", "G0114", taken), "rs30ultra-g0114")
        with self.assertRaises(SystemExit):
            naming.slug("XGIMI RS30 Ultra", None, taken)

    def test_model_py_and_publish_use_it(self):
        out = subprocess.run([sys.executable, os.path.join(G, "tools/release/model.py"), "g0111", "1.0.10-beta15"],
                             capture_output=True, text=True, check=True).stdout
        self.assertIn("TAG='x50-1.0.10-beta15'", out)
        self.assertIn("TITLE='Lumen OS 1.0.10-beta15 \u00b7 X50 Ultra (G0111)'", out)
        pub = open(os.path.join(G, "tools/release/publish.sh"), encoding="utf-8").read()
        self.assertIn('name="$TITLE"', pub)
        self.assertNotIn("(beta)", pub)


class Table(unittest.TestCase):
    CAT = {"models": [model("G0082", install=True), model("G0058", way="computer"), model("H0063"),
                      model("G0130", install=True), model("G0148", install=True), model("G9999")]}
    REL = [rel("z9x-1.0.10", False), rel("z9x-1.1.0-beta4"), rel("z9x-1.1.0-beta3"), rel("z9x"),
           rel("play6-1.0.10", False), rel("play6-1.0.10-beta4"), rel("titanmax-1.0.10-beta7"),
           rel("titanmax-1.0.10-beta6"), rel("v1.0.7", False)]

    def test_rows(self):
        rs = {r["c"]: r for r in models_table.rows(self.CAT, self.REL)}
        self.assertEqual(sorted(rs), ["dragon_healthy", "h0063", "horse_popular"])  # HB: no release in this list
        self.assertEqual((rs["horse_popular"]["stable"], rs["horse_popular"]["beta"]), ("1.0.10", "1.1.0-beta4"))
        self.assertEqual((rs["dragon_healthy"]["stable"], rs["dragon_healthy"]["beta"]), ("1.0.10", None))
        self.assertEqual([rs[c]["what"] for c in ("horse_popular", "dragon_healthy", "h0063")],
                         ["install", "computer", "live"])
        self.assertEqual(rs["h0063"]["guide"], "x50")
        self.assertEqual(rs["horse_popular"]["guide"], "z9x")

    def test_tables_follow_the_wording_rules(self):
        rs = models_table.rows(self.CAT, self.REL)
        for kind in ("install", "installer"):
            for lang in ("en", "ru"):
                t = models_table.table(kind, lang, rs, "docs/install/")
                for bad in (";", "\u2014", "\u2013", " - "):
                    self.assertNotIn(bad, t, (kind, lang))
        en = models_table.table("install", "en", rs, "docs/install/")
        self.assertIn("Install Lumen OS permanently", en)
        self.assertIn("| XGIMI TITAN Noir Max | H0063 | Lumen OS Live only, beta | [guide](docs/install/x50/en.md) |", en)
        self.assertIn("\u0423\u0441\u0442\u0430\u043d\u043e\u0432\u0438\u0442\u044c Lumen OS "
                      "\u043d\u0430\u0441\u043e\u0432\u0441\u0435\u043c", models_table.table("install", "ru", rs, ""))

    def test_every_default_file_has_its_block(self):
        rs = models_table.rows(self.CAT, self.REL)
        for f in models_table.FILES:
            old, new = models_table.render(os.path.join(G, f), rs)
            self.assertIn("<!-- models:end -->", new, f)


if __name__ == "__main__":
    unittest.main()
