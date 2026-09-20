#!/usr/bin/env python3
import os

D = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                 "app", "src", "main", "res", "drawable")
os.makedirs(D, exist_ok=True)

V = '<?xml version="1.0" encoding="utf-8"?>\n'
NS = ('xmlns:android="http://schemas.android.com/apk/res/android"')

shapes = {
    "divider": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/divider" /></shape>',
    "bg_gold_soft": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/herbal_beige" />'
                    f'<corners android:radius="16dp" /><stroke android:width="1dp" android:color="@color/herbal_gold" /></shape>',
    "bg_green_soft": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/herbal_green_pale" />'
                     f'<corners android:radius="16dp" /></shape>',
    "bg_status_chip": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/herbal_green_pale" />'
                      f'<corners android:radius="20dp" />'
                      f'<padding android:left="10dp" android:top="4dp" android:right="10dp" android:bottom="4dp" /></shape>',
    "bg_qr_frame": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/white" />'
                   f'<corners android:radius="18dp" /><stroke android:width="2dp" android:color="@color/herbal_green" />'
                   f'<padding android:left="16dp" android:top="16dp" android:right="16dp" android:bottom="16dp" /></shape>',
    "bg_amount_chip": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/white" />'
                      f'<corners android:radius="12dp" /><stroke android:width="1.5dp" android:color="@color/herbal_gold" /></shape>',
    "bg_amount_chip_active": f'{V}<shape {NS} android:shape="rectangle"><solid android:color="@color/herbal_green_pale" />'
                             f'<corners android:radius="12dp" /><stroke android:width="2dp" android:color="@color/herbal_green" /></shape>',
}

splash = f'''{V}<layer-list {NS}>
    <item android:drawable="@color/herbal_green_dark" />
    <item android:gravity="center" android:width="160dp" android:height="160dp" android:drawable="@mipmap/ic_launcher" />
</layer-list>
'''

icons = {
    "ic_home": "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z",
    "ic_shop": "M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6C4.9,6 4,6.9 4,8v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8C20,6.9 19.1,6 18,6zM12,4c1.1,0 2,0.9 2,2h-4C10,4.9 10.9,4 12,4zM18,20H6V8h2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8h4v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V8h2V20z",
    "ic_task": "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,6c-3.31,0 -6,2.69 -6,6h2c0,-2.21 1.79,-4 4,-4s4,1.79 4,4h2C18,8.69 15.31,6 12,6zM12,10c-1.1,0 -2,0.9 -2,2s0.9,2 2,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    "ic_wallet": "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    "ic_profile": "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
    "ic_cart": "M7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM1,2v2h2l3.6,7.59 -1.35,2.45c-0.16,0.28 -0.25,0.61 -0.25,0.96 0,1.1 0.9,2 2,2h12v-2H7.42c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12 0.9,-1.63h7.45c0.75,0 1.41,-0.41 1.75,-1.03l3.58,-6.49c0.08,-0.14 0.12,-0.31 0.12,-0.48 0,-0.55 -0.45,-1 -1,-1H5.21l-0.94,-2L1,2zM17,18c-1.1,0 -1.99,0.9 -1.99,2s0.89,2 1.99,2 2,-0.9 2,-2 -0.9,-2 -2,-2z",
    "ic_arrow_back": "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z",
    "ic_search": "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z",
    "ic_leaf": "M17,8C8,10 5.9,16.17 3.82,21.34L5.71,22l1.98,-4.86C9.06,18.03 10.46,18.5 12,18.5c5.5,0 9,-4.5 9,-12C17,8 17,8 17,8zM12,16.5c-1.5,0 -2.86,-0.44 -3.96,-1.23C9.15,12.82 12.3,11 17,10.78 16.6,14.3 15,16.5 12,16.5z",
    "ic_logout": "M17,7l-1.41,1.41L18.17,11H8v2h10.17l-2.58,2.58L17,17l5,-5zM4,5h8V3H4c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h8v-2H4V5z",
    "ic_share": "M18,16.08c-0.76,0 -1.44,0.3 -1.96,0.77L8.91,12.7c0.05,-0.23 0.09,-0.46 0.09,-0.7s-0.04,-0.47 -0.09,-0.7l7.05,-4.11c0.54,0.5 1.25,0.81 2.04,0.81 1.66,0 3,-1.34 3,-3s-1.34,-3 -3,-3 -3,1.34 -3,3c0,0.24 0.04,0.47 0.09,0.7L8.04,9.81C7.5,9.31 6.79,9 6,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3c0.79,0 1.5,-0.31 2.04,-0.81l7.12,4.16c-0.05,0.21 -0.08,0.43 -0.08,0.65 0,1.61 1.31,2.92 2.92,2.92s2.92,-1.31 2.92,-2.92 -1.31,-2.92 -2.92,-2.92z",
    "ic_copy": "M16,1H4c-1.1,0 -2,0.9 -2,2v14h2V3h12V1zM19,5H8c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z",
    "ic_refresh": "M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -8,8s3.58,8 8,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z",
    "ic_add": "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    "ic_chevron_right": "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z",
    "ic_receipt": "M19,3H5c-1.1,0 -2,0.9 -2,2v14l4,-4h12c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM14,11h-4v-2h4v2zM18,7H6V5h12v2z",
    "ic_check_circle": "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM10,17l-5,-5 1.41,-1.41L10,14.17l7.59,-7.59L19,8l-9,9z",
    "ic_ads": "M21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h3l3,3v-3h12c1.1,0 2,-0.9 2,-2V5C23,3.9 22.1,3 21,3zM8,14H6v-4h2v4zM11,14H9V6h2v8zM14,14h-2v-6h2v6zM17,14h-2V9h2v5z",
    "ic_person_add": "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM6,10L6,7L4,7v3L1,10v2h3v3h2v-3h3v-2L6,10zM15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
    "ic_shopping_bag": "M18,6h-2c0,-2.21 -1.79,-4 -4,-4S8,3.79 8,6H6C4.9,6 4,6.9 4,8v12c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V8C20,6.9 19.1,6 18,6zM12,4c1.1,0 2,0.9 2,2h-4C10,4.9 10.9,4 12,4z",
    "ic_lock": "M18,8h-1V6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2H6c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8H8.9V6c0,-1.71 1.39,-3.1 3.1,-3.1s3.1,1.39 3.1,3.1v2z",
    "ic_info": "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2V7h2v2z",
    "ic_shield": "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5L12,1zM12,11.99h7c-0.53,4.12 -3.28,7.79 -7,8.94V12H5V6.3l7,-3.11v8.8z",
    "ic_star": "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z",
}

for name, content in shapes.items():
    open(os.path.join(D, name + ".xml"), "w").write(content)

open(os.path.join(D, "splash_background.xml"), "w").write(splash)

for name, path in icons.items():
    open(os.path.join(D, name + ".xml"), "w").write(
        f'{V}<vector {NS}\n    android:width="24dp" android:height="24dp"\n'
        f'    android:viewportWidth="24" android:viewportHeight="24"\n'
        f'    android:tint="?attr/colorControlNormal">\n'
        f'    <path android:fillColor="@android:color/white" android:pathData="{path}" />\n'
        f'</vector>\n')

print(f"wrote {len(shapes)} shapes, 1 splash, {len(icons)} icons to {D}")