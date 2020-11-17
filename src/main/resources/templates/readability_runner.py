from readability import Document

content = r"""{{{ content }}}"""
url = r"{{{ url }}}"

doc = Document(content, url=url)

# remove wrapping html/body tags
summary = doc.summary()
clean_summary = summary.replace("<html>", "").replace("</html>", "").replace("<body>", "").replace("</body>", "")

output = r"{{{ output }}}"

with open(output, 'w', encoding="utf8") as f:
    f.write(doc.short_title() + "\n")
    f.write(clean_summary)
