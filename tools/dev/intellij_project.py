#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""intellij_project.py: write an IntelliJ IDEA project for the gsi/ tree (local only, .idea/ is git-ignored).

    python3 tools/dev/intellij_project.py        (run from anywhere; writes gsi/.idea, replacing it)

Then in IntelliJ: File > Open > the gsi folder. One module "gsi" shows the whole tree (build outputs, logs and
third-party sources excluded); every app with Java sources (apps/<App>/src) is its own module with src as sources,
test as test sources, and the Android framework headers (apps/sdk/framework-full-headers.jar) as a library, so
navigation and highlighting work. The shared modules apps/core/* and apps/common/* are modules too and every
module depends on them, the way the recipes assemble an app from its folder and those modules. Nothing is built
by IntelliJ: the apps are built by apps/build_apk.sh and the per-app scripts. the HB app is apps/LumenProjector/core with the aidl-gmpf adapter and its manifest patch
(devices/goat_elegant/app/HbProjector.layers).
"""
import os
import shutil
from xml.sax.saxutils import quoteattr

GSI = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
IDEA = os.path.join(GSI, ".idea")
FRAMEWORK = "apps/sdk/framework-full-headers.jar"
EXCLUDE = ["build", "logs", "research", "backup", "brag-output", "play6", "apps/out", "apps/third_party",
           "installer/tools", "docs/repo/hold"]


def write(rel, text):
    p = os.path.join(IDEA, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)


def apps():
    """(module name, folder) of every Java tree: apps/<App>, then the shared modules apps/core/* and apps/common/*."""
    out = []
    for group, prefix in (("apps", ""), ("apps/core", "core-"), ("apps/common", "common-")):
        root = os.path.join(GSI, group)
        for name in sorted(os.listdir(root)):
            if os.path.isdir(os.path.join(root, name, "src")):
                out.append((prefix + name, group + "/" + name))
    return out


def app_iml(folder, deps):
    base = "$MODULE_DIR$/../../" + folder
    folders = ['      <sourceFolder url="file://%s/src" isTestSource="false" />' % base]
    if os.path.isdir(os.path.join(GSI, folder, "test")):
        folders.append('      <sourceFolder url="file://%s/test" isTestSource="true" />' % base)
    # an app tree is assembled from its folder plus shared modules (devices/*/app/*.layers): every module sees the
    # shared ones, so navigation crosses into apps/core and apps/common (IntelliJ builds nothing, cycles are fine)
    entries = "".join('    <orderEntry type="module" module-name="%s" />\n' % d for d in deps)
    return """<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://%s">
%s
      <excludeFolder url="file://%s/build" />
    </content>
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
    <orderEntry type="library" name="android-framework" level="project" />
%s  </component>
</module>
""" % (base, "\n".join(folders), base, entries)


def main():
    if os.path.isdir(IDEA):
        shutil.rmtree(IDEA)
    mods_found = apps()
    names = [n for n, _ in mods_found]
    shared = [n for n, f in mods_found if f.startswith(("apps/core/", "apps/common/"))]
    excl = "\n".join('      <excludeFolder url="file://$MODULE_DIR$/../../%s" />' % e for e in EXCLUDE
                     if os.path.exists(os.path.join(GSI, e)))
    write("modules/gsi.iml", """<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$/../..">
%s
    </content>
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
  </component>
</module>
""" % excl)
    for n, folder in mods_found:
        write("modules/%s.iml" % n, app_iml(folder, [d for d in shared if d != n]))
    mods = ['      <module fileurl="file://$PROJECT_DIR$/.idea/modules/gsi.iml" filepath="$PROJECT_DIR$/.idea/modules/gsi.iml" />']
    mods += ['      <module fileurl="file://$PROJECT_DIR$/.idea/modules/%s.iml" filepath="$PROJECT_DIR$/.idea/modules/%s.iml" />'
             % (n, n) for n in names]
    write("modules.xml", """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
%s
    </modules>
  </component>
</project>
""" % "\n".join(mods))
    write("libraries/android_framework.xml", """<component name="libraryTable">
  <library name="android-framework">
    <CLASSES>
      <root url=%s />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
""" % quoteattr("jar://$PROJECT_DIR$/%s!/" % FRAMEWORK))
    write("misc.xml", """<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectRootManager" version="2" languageLevel="JDK_17" project-jdk-name="21" project-jdk-type="JavaSDK">
    <output url="file://$PROJECT_DIR$/.idea/out" />
  </component>
</project>
""")
    write(".name", "Lumen OS (gsi)\n")
    print("intellij_project: %d app modules, %d shared modules + gsi -> %s" % (len(names) - len(shared), len(shared), IDEA))


if __name__ == "__main__":
    main()
