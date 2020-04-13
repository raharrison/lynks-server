from readability import Document

content = """{{{ content }}}"""
url = "{{{ url }}}"

doc = Document(content, url)

# remove wrapping html/body tags
summary = doc.summary()
clean_summary = summary.replace("<html>", "").replace("</html>", "").replace("<body>", "").replace("</body>", "")

output = "{{{ output }}}"

with open(output, 'w', encoding="utf8") as f:
    f.write(doc.short_title() + "\n")
    f.write(clean_summary)
