#!/usr/bin/env python3
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
XML = os.path.join(RES, "xml")
DRW = os.path.join(RES, "drawable")
os.makedirs(XML, exist_ok=True)

V = '<?xml version="1.0" encoding="utf-8"?>\n'

files = {
    os.path.join(XML, "network_security_config.xml"): V + """<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
""",
    os.path.join(XML, "data_extraction_rules.xml"): V + """<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
        <exclude domain="file" path="." />
        <exclude domain="root" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
        <exclude domain="file" path="." />
        <exclude domain="root" path="." />
    </device-transfer>
</data-extraction-rules>
""",
    os.path.join(XML, "file_paths.xml"): V + """<paths>
    <cache-path name="qr_cache" path="qr/" />
    <external-cache-path name="qr_external" path="qr/" />
    <files-path name="qr_files" path="qr/" />
</paths>
""",
    os.path.join(DRW, "ic_launcher_background.xml"): V + """
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="@color/herbal_green_dark"
        android:endColor="@color/herbal_green"
        android:type="linear" />
</shape>
""",
}

for path, content in files.items():
    open(path, "w").write(content)
    print("wrote", os.path.relpath(path, ROOT))