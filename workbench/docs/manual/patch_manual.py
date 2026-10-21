import re

p = "workbench/docs/manual/build_manual.py"
s = open(p, encoding="utf-8").read()

# 1) 表格：固定布局 + 列宽 + 表头重复 + 行不跨页
s = s.replace(
    '''def table(headers, rows, widths=None):
    t = document.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER''',
    '''def set_fixed_layout(t, widths):
    t.autofit = False
    tblPr = t._tbl.tblPr
    layout = OxmlElement("w:tblLayout")
    layout.set(qn("w:type"), "fixed")
    tblPr.append(layout)
    for index, width in enumerate(widths):
        t.columns[index].width = Cm(width)
        for cell in t.columns[index].cells:
            cell.width = Cm(width)


def table(headers, rows, widths=None):
    t = document.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER''',
)

s = s.replace(
    '''    if widths:
        for row in t.rows:
            for index, width in enumerate(widths):
                row.cells[index].width = Cm(width)
    document.add_paragraph().paragraph_format.space_after = Pt(2)
    return t''',
    '''    if widths:
        set_fixed_layout(t, widths)
    # 表头跨页重复
    trPr = t.rows[0]._tr.get_or_add_trPr()
    header_flag = OxmlElement("w:tblHeader")
    header_flag.set(qn("w:val"), "true")
    trPr.append(header_flag)
    # 单行内容不拆到两页
    for row in t.rows:
        row_pr = row._tr.get_or_add_trPr()
        cant = OxmlElement("w:cantSplit")
        cant.set(qn("w:val"), "true")
        row_pr.append(cant)
    document.add_paragraph().paragraph_format.space_after = Pt(2)
    return t''',
)

# 2) 列表样式的中文字体（消除 Courier 缺失告警）
s = s.replace(
    '''normal = document.styles["Normal"]''',
    '''for style_name in ("List Bullet", "List Number"):
    try:
        set_cjk(document.styles[style_name], 10.5)
    except KeyError:
        pass

normal = document.styles["Normal"]''',
)

# 3) 封面：加 Logo，收紧留白
s = s.replace(
    '''# ---------- 封面 ----------
for _ in range(4):
    document.add_paragraph()
para("星图", 40, True, ACCENT, WD_ALIGN_PARAGRAPH.CENTER, space_after=0)''',
    '''# ---------- 封面 ----------
for _ in range(3):
    document.add_paragraph()
logo = document.add_paragraph()
logo.alignment = WD_ALIGN_PARAGRAPH.CENTER
logo.add_run().add_picture("../../brand/png/icon-192.png", width=Cm(3.2))
document.add_paragraph()
para("星图", 40, True, ACCENT, WD_ALIGN_PARAGRAPH.CENTER, space_after=0)''',
)

s = s.replace(
    '''for _ in range(6):
    document.add_paragraph()
table(''',
    '''for _ in range(5):
    document.add_paragraph()
table(''',
)

# 4) 页脚：文档名 + 页码
s = s.replace(
    '''# ---------- 封面 ----------''',
    '''# ---------- 页脚 ----------
footer_paragraph = section.footer.paragraphs[0]
footer_paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
run_left = footer_paragraph.add_run("星图 · 用户手册　")
set_cjk(run_left, 9, False, GREY)
run_page = footer_paragraph.add_run()
set_cjk(run_page, 9, False, GREY)
fld_begin = OxmlElement("w:fldChar")
fld_begin.set(qn("w:fldCharType"), "begin")
instr = OxmlElement("w:instrText")
instr.set(qn("xml:space"), "preserve")
instr.text = "PAGE"
fld_end = OxmlElement("w:fldChar")
fld_end.set(qn("w:fldCharType"), "end")
run_page._r.append(fld_begin)
run_page._r.append(instr)
run_page._r.append(fld_end)

# ---------- 封面 ----------''',
)

open(p, "w", encoding="utf-8").write(s)
print("build script patched")
