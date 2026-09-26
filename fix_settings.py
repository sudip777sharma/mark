import sys

with open('ui/src/components/settings/SettingsView.tsx', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace(
    \"setEditForm({ ...editForm, apiKeys: [...currentKeys, ''] });\",
    \"setEditForm({ ...editForm, apiKeys: [...currentKeys, { keyName: '', keyValue: '' }] });\"
)

c = c.replace(
    \"const handleKeyChange = (index: number, value: string) => {\",
    \"const handleKeyChange = (index: number, field: 'keyName' | 'keyValue', value: string) => {\"
)

c = c.replace(
    \"currentKeys[index] = value;\",
    \"currentKeys[index] = { ...currentKeys[index], [field]: value };\"
)

c = c.replace(
    \"{(editForm.apiKeys || []).map((key, index) => (\",
    \"{(editForm.apiKeys || []).map((keyEntry, index) => (\"
)

old_input = '''<input 
                    type=\"text\" 
                    value={key} 
                    onChange={(e) => handleKeyChange(index, e.target.value)}
                    placeholder=\"Enter API Key...\"
                    style={{
                      flex: 1,
                      background: 'rgba(255, 255, 255, 0.05)',
                      border: '1px solid var(--border-subtle)',
                      color: '#fff',
                      padding: '10px 14px',
                      borderRadius: 6,
                      fontSize: 14,
                      outline: 'none'
                    }}
                  />'''

new_input = '''<input 
                    type=\"text\" 
                    value={keyEntry.keyName || ''} 
                    onChange={(e) => handleKeyChange(index, 'keyName', e.target.value)}
                    placeholder=\"Optional Label (e.g. Work)\"
                    style={{
                      width: '150px',
                      background: 'rgba(255, 255, 255, 0.05)',
                      border: '1px solid var(--border-subtle)',
                      color: '#fff',
                      padding: '10px 14px',
                      borderRadius: 6,
                      fontSize: 14,
                      outline: 'none'
                    }}
                  />
                  <input 
                    type=\"text\" 
                    value={keyEntry.keyValue} 
                    onChange={(e) => handleKeyChange(index, 'keyValue', e.target.value)}
                    placeholder=\"Enter API Key...\"
                    style={{
                      flex: 1,
                      background: 'rgba(255, 255, 255, 0.05)',
                      border: '1px solid var(--border-subtle)',
                      color: '#fff',
                      padding: '10px 14px',
                      borderRadius: 6,
                      fontSize: 14,
                      outline: 'none'
                    }}
                  />'''

c = c.replace(old_input, new_input)

with open('ui/src/components/settings/SettingsView.tsx', 'w', encoding='utf-8') as f:
    f.write(c)

print('Done replacing.')
