import React, { useEffect, useState } from 'react';
import { Save, Settings } from 'lucide-react';

export const AdvancedSettings: React.FC = () => {
  const [prefs, setPrefs] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetch('/api/preferences')
      .then(r => r.json())
      .then(data => setPrefs(data))
      .catch(e => console.error(e));
  }, []);

  const handleChange = (key: string, value: string) => {
    setPrefs(prev => ({ ...prev, [key]: value }));
  };

  const saveConfig = async () => {
    setSaving(true);
    try {
      await fetch('/api/preferences', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(prefs)
      });
      alert('Preferences saved successfully!');
    } catch (e) {
      alert('Failed to save preferences');
    }
    setSaving(false);
  };

  const defaultKeys = [
    { key: 'agent.maxSteps', label: 'Max Steps', type: 'number', placeholder: '30' },
    { key: 'agent.maxHistoryLength', label: 'Max History Length (messages)', type: 'number', placeholder: '100' },
    { key: 'agent.maxHistoryChars', label: 'Max History Chars', type: 'number', placeholder: '2000000' },
    { key: 'browser.headless', label: 'Browser Headless (true/false)', type: 'text', placeholder: 'false' },
    { key: 'browser.timeoutMs', label: 'Browser Timeout (ms)', type: 'number', placeholder: '10000' },
    { key: 'filesystem.basePath', label: 'File System Base Path', type: 'text', placeholder: './mark-workspace' },
    { key: 'ui.maxDepth', label: 'UI Parse Max Depth', type: 'number', placeholder: '7' }
  ];

  return (
    <div style={{ padding: 20, color: '#fff', display: 'flex', flexDirection: 'column', gap: 20 }}>
      <h2 style={{ display: 'flex', alignItems: 'center', gap: 10 }}><Settings /> Advanced Preferences</h2>
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: 15, maxWidth: 500 }}>
        {defaultKeys.map(item => (
          <div key={item.key} style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
            <label style={{ fontSize: 13, color: 'var(--text-secondary)' }}>{item.label} ({item.key})</label>
            <input 
              type={item.type}
              value={prefs[item.key] || ''}
              onChange={e => handleChange(item.key, e.target.value)}
              placeholder={`Default: ${item.placeholder}`}
              style={{
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-subtle)',
                color: '#fff',
                padding: '10px 14px',
                borderRadius: 6,
                outline: 'none'
              }}
            />
          </div>
        ))}
      </div>

      <button 
        onClick={saveConfig}
        disabled={saving}
        style={{
          background: 'var(--accent-cyan)',
          color: '#07090e',
          border: 'none',
          padding: '10px 24px',
          borderRadius: 6,
          fontWeight: 600,
          cursor: saving ? 'not-allowed' : 'pointer',
          width: 'fit-content',
          display: 'flex',
          alignItems: 'center',
          gap: 8
        }}
      >
        <Save size={16} />
        {saving ? 'Saving...' : 'Save Preferences'}
      </button>
    </div>
  );
};
