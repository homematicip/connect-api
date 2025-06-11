import json
import sys

version = sys.argv[1]

metadataFile = open('metadata.json', mode="r+", encoding="utf8")
changelogFile = open('changelog.txt', 'r', encoding="utf8")
changelog = changelogFile.read()
metaDataRead = metadataFile.read()
data = json.loads(metaDataRead)
data['version'] = version
data['changelog'] = changelog
# print(data)
newJsonString = json.dumps(json.dumps(data))
print(newJsonString)

metadataFile.seek(0)
metadataFile.write(json.dumps(data, indent=4, sort_keys=True, ensure_ascii=False))
metadataFile.truncate()
metadataFile.close()