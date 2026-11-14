#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Host test of lumen_stats.py: python3 tools/stats/server/test_lumen_stats.py"""
import json
import os
import sqlite3
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lumen_stats as ls  # noqa: E402

GSI = os.environ.get("LUMEN_GSI") or os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))))

ID1 = "0f8fad5b-d9cb-469f-a165-70867728950e"
ID2 = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
ID3 = "1b4e28ba-2fa1-41d2-883f-0016d3cca427"
SERIAL = "CZW1234567MK"
DAY = 86400


def ping(iid=ID1, **kw):
    p = {"v": 2, "id": iid, "serial": SERIAL, "model": "goat_elegant", "xgimi_model": "G0130",
         "version": "1.0.8-beta3", "version_code": 1000803, "build": "lumen-hb-1.0.8-beta3", "channel": "beta",
         "prev_version": "", "prev_version_code": 0, "method": ""}
    p.update(kw)
    return p


class FakeGeo:
    def lookup(self, addr):
        return "RU" if addr == "203.0.113.9" else ""


class T(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.db = os.path.join(self.tmp, "s.db")
        self.store = ls.Store(self.db, ls.load_salt(os.path.join(self.tmp, "salt")))

    def test_validate(self):
        p = ls.validate(ping())
        self.assertEqual((p["id"], p["model"], p["version"], p["channel"], p["kind"]),
                         (ID1, "goat_elegant", "1.0.8-beta3", "beta", ""))
        self.assertEqual(ls.validate(ping(model="mt5877", xgimi_model="G0082"))["model"], "horse_popular")
        self.assertEqual(ls.validate(ping(model="Weird Model!"))["model"], "other")
        # the channel is the build's: v 2 clients sent their beta switch (a beta build with it off said stable)
        self.assertEqual(ls.validate(ping(version="1.0.10-beta9", channel="stable"))["channel"], "beta")
        self.assertEqual(ls.validate(ping(version="1.0.9", channel="beta"))["channel"], "stable")
        self.assertEqual(ls.validate(ping(version="x50max-1.0.10-rc1", channel="stable"))["channel"], "beta")
        # the registry codename: one product image, several models
        for model, xm, want in (("horse_popular", "G0082", "horse_popular"), ("horse_popular", "G0111", "g0111"),
                                ("horse_popular", "G0100", "g0100"), ("horse_popular", "G0155", "g0155"),
                                ("mt5877", "G0111", "g0111"), ("g0155", "G0155", "g0155"),
                                ("play6", "G0058", "dragon_healthy"), ("goat_elegant", "G0118", "goat_elegant")):
            self.assertEqual(ls.validate(ping(model=model, xgimi_model=xm))["model"], want, (model, xm))
        self.assertEqual(ls.validate(ping(v=3, trial=True))["kind"], "trial")
        self.assertEqual(ls.validate(ping(v=3, trial=False))["kind"], "installed")
        v1 = {"v": 1, "id": ID1, "model": "mt5877", "version": "1.0.8", "version_code": 10009, "build": "b",
              "channel": "stable"}
        self.assertEqual(ls.validate(v1)["serial"], "")
        for bad in (ping(id="x"), ping(v=4), ping(trial="yes"), ping(trial=1), ping(channel="dev"), ping(version="1.0 <b>"), ping(version_code="1"),
                    ping(version_code=True), ping(build="a b"), ping(serial="a/b"), ping(method="hack"), [], "x"):
            with self.assertRaises(ValueError):
                ls.validate(bad)

    def test_no_raw_identifiers_stored(self):
        self.store.add(ls.validate(ping(method="first")), "RU", now=1000 * DAY)
        con = sqlite3.connect(self.db)
        dump = "\n".join(con.iterdump())
        self.assertNotIn(ID1, dump)
        self.assertNotIn(SERIAL, dump)
        cols = [r[1] for r in con.execute("PRAGMA table_info(pings2)")]
        self.assertNotIn("ip", " ".join(cols))
        self.assertNotIn("city", cols)
        self.assertEqual(con.execute("SELECT country FROM pings2").fetchone(), ("RU",))

    def test_registry(self):
        """FAMILIES and NAMES are the device registry (devices/<codename>/device.conf), every supported device."""
        sys.path.insert(0, os.path.join(GSI, "tools", "devices"))
        import devices
        fam, names = {}, {}
        for code, e in devices.load(GSI).items():
            for m in e["models"]:
                fam.setdefault(e.get("family") or code, {})[m] = code
            names[code] = e["name"]
        self.assertEqual(ls.FAMILIES, fam)
        self.assertEqual(ls.NAMES, names)

    def test_counts_and_updates(self):
        t = 1000 * DAY
        s = self.store
        s.add(ls.validate(ping(ID1, version="1.0.7", version_code=1000799, method="first")), now=t - 20 * DAY)
        s.add(ls.validate(ping(ID1, version="1.0.8", version_code=1000899, prev_version="1.0.7",
                               prev_version_code=1000799, method="ota_delta")), now=t - 2 * DAY)
        s.add(ls.validate(ping(ID1, version="1.0.8", version_code=1000899)), now=t - 2 * DAY)
        # same projector, reset: new install id, same serial
        s.add(ls.validate(ping(ID3, version="1.0.8", version_code=1000899, method="first")), now=t - 1 * DAY)
        s.add(ls.validate(ping(ID2, serial="Z9XSERIAL", model="mt5877", xgimi_model="G0082", version="1.0.8",
                               version_code=10009, channel="stable")), "DE", now=t)
        st = json.loads(s.stats(now=t))
        a7 = st["active"]["7"]
        self.assertEqual((a7["units"], a7["ids"]), (2, 3))
        self.assertEqual({(r["model"], r["xgimi_model"]) for r in a7["by_model"]},
                         {("goat_elegant", "G0130"), ("horse_popular", "G0082")})
        self.assertIn({"country": "DE", "units": 1}, a7["by_country"])
        # the reset projector counts once in every breakdown (by its serial), not once per install id
        self.assertEqual(sum(r["units"] for r in a7["by_version"] if r["model"] == "goat_elegant"), 1)
        self.assertEqual(sum(r["units"] for r in st["daily"] if r["model"] == "goat_elegant"
                             and r["day"] == st["daily"][-1]["day"]), 0)
        self.assertNotIn("by_city", a7)
        self.assertEqual(st["pairs"], [{"model": "goat_elegant", "from_version": "1.0.7", "to_version": "1.0.8",
                                        "method": "ota_delta", "units": 1}])
        self.assertEqual(st["reinstalled"], 1)
        # two projectors new in the history: the reset one is not new again, and "first" is no update
        self.assertEqual(sum(r["units"] for r in st["new_daily"]), 2)
        self.assertNotIn("first", {r["method"] for r in st["updates_daily"]})
        self.assertEqual(st["names"]["g0111"], "XGIMI X50 Ultra")
        # old rows are pruned
        s.add(ls.validate(ping(ID2)), now=t + ls.KEEP_DAYS * DAY)
        self.assertEqual(sqlite3.connect(self.db).execute("SELECT COUNT(*) FROM pings2").fetchone()[0], 2)

    def test_trial_and_installed_one_projector(self):
        """A DSU trial run and the installed system of one projector on the same day (two install ids, one serial):
        one projector in the day, in the window, in the new ones; the installed row stands for it."""
        t = 1000 * DAY
        s = self.store
        s.add(ls.validate(ping(ID1, v=3, trial=True, model="g0111", xgimi_model="G0111", version="x50-1.0.10-beta3",
                               version_code=0, method="first")), "RU", now=t)
        s.add(ls.validate(ping(ID2, v=3, trial=False, model="g0111", xgimi_model="G0111", version="x50-1.0.10-beta4",
                               version_code=0, method="first")), "RU", now=t)
        # a second projector, trial only
        s.add(ls.validate(ping(ID3, v=3, trial=True, serial="CL45OTHER", model="horse_popular", xgimi_model="G0155",
                               version="rs30-1.0.10-beta1", version_code=0)), "AT", now=t)
        st = json.loads(s.stats(now=t))
        a7 = st["active"]["7"]
        self.assertEqual((a7["units"], a7["ids"]), (2, 3))
        self.assertEqual(sorted((r["model"], r["kind"], r["units"]) for r in a7["by_kind"]),
                         [("g0111", "installed", 1), ("g0155", "trial", 1)])
        self.assertEqual(sorted((r["model"], r["version"], r["channel"], r["units"]) for r in st["daily"]),
                         [("g0111", "x50-1.0.10-beta4", "beta", 1), ("g0155", "rs30-1.0.10-beta1", "beta", 1)])
        self.assertEqual(sum(r["units"] for r in st["new_daily"]), 2)
        self.assertEqual(sum(r["units"] for r in a7["by_country"]), 2)

    def test_migration_v2(self):
        """A schema 2 database (the VPS until 2026-11-13): backed up first, then the kind column, the channel from
        the version and the registry codename; no row is lost or added."""
        db = os.path.join(self.tmp, "v2.db")
        con = sqlite3.connect(db)
        con.executescript("""
CREATE TABLE pings2 (day TEXT NOT NULL, id_hash TEXT NOT NULL, serial_hash TEXT NOT NULL, model TEXT NOT NULL,
  xgimi_model TEXT NOT NULL, version TEXT NOT NULL, version_code INTEGER NOT NULL, build TEXT NOT NULL,
  channel TEXT NOT NULL, country TEXT NOT NULL, PRIMARY KEY (day, id_hash));
CREATE TABLE updates2 (day TEXT NOT NULL, id_hash TEXT NOT NULL, serial_hash TEXT NOT NULL, model TEXT NOT NULL,
  from_version TEXT NOT NULL, from_code INTEGER NOT NULL, to_version TEXT NOT NULL, to_code INTEGER NOT NULL,
  method TEXT NOT NULL, PRIMARY KEY (id_hash, from_code, to_code));
INSERT INTO pings2 VALUES ('2026-11-11', 'a', 's1', 'goat_elegant', 'G0130', '1.0.10-beta9', 1001009, 'b', 'stable', 'RU');
INSERT INTO pings2 VALUES ('2026-11-12', 'b', 's2', 'horse_popular', 'G0111', 'x50-1.0.10-beta3', 0, 'b', 'stable', 'RU');
INSERT INTO pings2 VALUES ('2026-11-12', 'c', 's3', 'horse_popular', 'G0082', '1.0.10', 10012, 'b', 'beta', 'RU');
INSERT INTO updates2 VALUES ('2026-11-12', 'b', 's2', 'horse_popular', '', 0, 'x50-1.0.10-beta3', 0, 'first');
INSERT INTO updates2 VALUES ('2026-11-12', 'c', 's3', 'mt5877', '1.0.9', 10011, '1.0.10', 10012, 'ota_delta');
""")
        con.commit()
        con.close()
        st = ls.Store(db, b"k", now=1000 * DAY)
        self.assertTrue(st.backup and os.path.exists(st.backup))
        bk = sqlite3.connect(st.backup)
        self.assertEqual(bk.execute("SELECT COUNT(*) FROM pings2").fetchone()[0], 3)
        self.assertEqual(bk.execute("PRAGMA user_version").fetchone()[0], 0)
        con = sqlite3.connect(db)
        self.assertEqual(con.execute("PRAGMA user_version").fetchone()[0], ls.SCHEMA_VERSION)
        self.assertEqual(con.execute("SELECT id_hash, model, channel, kind FROM pings2 ORDER BY id_hash").fetchall(),
                         [("a", "goat_elegant", "beta", ""), ("b", "g0111", "beta", ""),
                          ("c", "horse_popular", "stable", "")])
        self.assertEqual(con.execute("SELECT id_hash, model FROM updates2 ORDER BY id_hash").fetchall(),
                         [("b", "g0111"), ("c", "horse_popular")])
        self.assertEqual(con.execute("SELECT COUNT(*) FROM updates2").fetchone()[0], 2)
        # a second start migrates nothing and makes no second backup
        self.assertIsNone(ls.Store(db, b"k").backup)

    def test_github_classification(self):
        """Release tags of every model and the Lumen Installer repository get their device, installer APKs count as
        installer downloads and the installer's device index as its starts."""
        sys.path.insert(0, os.path.join(GSI, "tools", "stats"))
        import github_downloads as gd
        for tag, dev in (("x50-1.0.10-beta3", "g0111"), ("x50max-1.0.10-beta3", "g0100"), ("x50", "g0111"),
                         ("rs30-1.0.10-beta1", "g0155"), ("play6-1.0.10-beta2", "dragon_healthy"),
                         ("z9x-hb-1.0.10-beta11", "goat_elegant"), ("z9x-1.0.10", "horse_popular"), ("v1.0.7", "horse_popular"),
                         ("lumen-installer-v1.0.1", "installer"), ("lumen-installer-index", "installer")):
            self.assertEqual(gd.device(tag), dev, tag)
        self.assertEqual(gd.kind("lumen-installer-0.5.apk"), "installer")
        self.assertEqual(gd.kind("devices.json"), "manifest")
        self.assertEqual(gd.kind("index.x509.pem"), "other")
        rows = gd.rows(gd.installer_tags([{"tag_name": "index", "prerelease": False, "published_at": "2026-11-12T00:00:00Z",
                                            "assets": [{"name": "devices.json", "download_count": 14}]}]), "2026-11-13")
        self.assertEqual((rows[0]["tag"], rows[0]["device"], rows[0]["channel"], rows[0]["kind"]),
                         ("lumen-installer-index", "installer", True, "manifest"))
        self.assertRegex(gd.utc_day(), r"^\d{4}-\d{2}-\d{2}$")

    def test_rate_limit(self):
        r = ls.RateLimit()
        self.assertTrue(all(r.allow("a", now=0) for _ in range(ls.RATE_MAX)))
        self.assertFalse(r.allow("a", now=1))
        self.assertTrue(r.allow("b", now=1))
        self.assertTrue(r.allow("a", now=ls.RATE_WINDOW + 1))

    def test_geo_without_db(self):
        self.assertEqual(ls.Geo("").lookup("8.8.8.8"), "")
        self.assertEqual(ls.Geo("/nonexistent").lookup("192.0.2.1"), "")

    def test_city_column_dropped(self):
        db = os.path.join(self.tmp, "old.db")
        con = sqlite3.connect(db)
        con.execute("CREATE TABLE pings2 (day TEXT NOT NULL, id_hash TEXT NOT NULL, serial_hash TEXT NOT NULL, "
                    "model TEXT NOT NULL, xgimi_model TEXT NOT NULL, version TEXT NOT NULL, version_code INTEGER "
                    "NOT NULL, build TEXT NOT NULL, channel TEXT NOT NULL, country TEXT NOT NULL, city TEXT NOT "
                    "NULL, PRIMARY KEY (day, id_hash))")
        con.execute("INSERT INTO pings2 VALUES ('2026-11-04', 'h', 's', 'm', 'x', '1', 1, 'b', 'stable', 'RU', 'Omsk')")
        con.commit()
        con.close()
        ls.Store(db, b"k")
        con = sqlite3.connect(db)
        self.assertNotIn("city", [r[1] for r in con.execute("PRAGMA table_info(pings2)")])
        self.assertEqual(con.execute("SELECT country FROM pings2").fetchall(), [("RU",)])
        self.assertNotIn(b"Omsk", open(db, "rb").read())

    def test_http(self):
        srv = ThreadingHTTPServer(("127.0.0.1", 0), ls.make_handler(self.store, ls.RateLimit(), FakeGeo()))
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        base = "http://127.0.0.1:%d/lumen/v1/" % srv.server_address[1]

        def post(body, cf=None):
            h = {"Content-Type": "application/json"}
            if cf:
                h["X-Lumen-CF"] = cf
            req = urllib.request.Request(base + "ping", data=body, headers=h)
            try:
                return urllib.request.urlopen(req).status
            except urllib.error.HTTPError as e:
                return e.code
        self.assertEqual(post(json.dumps(ping()).encode(), cf="203.0.113.9"), 204)
        self.assertEqual(post(b"{"), 400)
        self.assertEqual(post(b"x" * (ls.MAX_BODY + 1)), 413)
        s = json.loads(urllib.request.urlopen(base + "stats").read())
        self.assertEqual(s["active"]["7"]["units"], 1)
        self.assertEqual(s["active"]["7"]["by_country"], [{"country": "RU", "units": 1}])
        srv.shutdown()


if __name__ == "__main__":
    unittest.main()
