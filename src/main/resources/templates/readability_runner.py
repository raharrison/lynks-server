from readability import Document

content = """{{{ content }}}"""
url = "{{{ url }}}"

doc = Document(content, url)

output = "{{{ output }}}"

with open(output, 'w', encoding="utf8") as f:
    f.write(doc.short_title() + "\n")
    f.write(doc.summary())
