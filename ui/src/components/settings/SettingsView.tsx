import React, { useEffect, useState } from 'react';
import { useAgentStore } from '../../store/agentStore';
import { AdvancedSettings } from './AdvancedSettings';
import { Save, Key, Plus, Trash2, Server, KeySquare, Settings, Sparkles, CheckCircle2, Circle } from 'lucide-react';

interface LlmProviderConfig {
  id?: number;
  configName: string;
  providerType: string;
  activeModel: string;
  baseUrl: string;
  apiKeys: {keyName?: string, keyValue: string, isActive?: boolean}[];
  default: boolean;
}

const PROVIDER_TYPES = ['gemini', 'local', 'groq', 'openrouter', 'colab'];

export const SettingsView: React.FC = () => {
  const [configs, setConfigs] = useState<LlmProviderConfig[]>([]);
  const [selectedConfig, setSelectedConfig] = useState<string>('');
  const [editForm, setEditForm] = useState<Partial<LlmProviderConfig>>({});
  
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isCreatingNew, setIsCreatingNew] = useState(false);
  const [activeTab, setActiveTab] = useState<'llm' | 'advanced'>('llm');
  
  const setActiveProviderStore = useAgentStore(s => s.setActiveProvider);
  const setActiveModelStore = useAgentStore(s => s.setActiveModel);

  useEffect(() => {
    fetchAllConfigs();
  }, []);

  const fetchAllConfigs = async () => {
    try {
      const res = await fetch('/api/config/llm/all');
      if (res.ok) {
        const data: LlmProviderConfig[] = await res.json();
        setConfigs(data);
        
        const active = data.find(c => c.default);
        if (active) {
          setActiveProviderStore(active.configName, active.id || -1);
          setActiveModelStore(active.activeModel);
          if (!selectedConfig && !isCreatingNew) {
            setSelectedConfig(active.configName);
            setEditForm(active);
          }
        } else if (data.length > 0 && !selectedConfig && !isCreatingNew) {
          setSelectedConfig(data[0].configName);
          setEditForm(data[0]);
        } else if (data.length === 0) {
          handleCreateNew();
        }
      }
    } catch (e) {
      console.error("Failed to fetch configs", e);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectConfig = (configName: string) => {
    setIsCreatingNew(false);
    setSelectedConfig(configName);
    const existing = configs.find(c => c.configName === configName);
    if (existing) {
      setEditForm(existing);
    }
  };

  const handleCreateNew = () => {
    setIsCreatingNew(true);
    setSelectedConfig('');
    setEditForm({ configName: 'New Profile', providerType: 'gemini', activeModel: '', baseUrl: '', apiKeys: [] });
  };

  const handleToggleActive = async (configName: string) => {
    const existing = configs.find(c => c.configName === configName);
    if (!existing) {
       alert("Please save configuration for this provider first.");
       return;
    }
    
    try {
      await fetch('/api/config/llm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: existing.id,
            configName: configName,
          providerType: existing.providerType,
          model: existing.activeModel,
          baseUrl: existing.baseUrl,
          apiKeys: existing.apiKeys,
          isDefault: true
        })
      });
      await fetchAllConfigs();
    } catch (e) {
      console.error(e);
    }
  };

  const saveConfig = async () => {
    if (!editForm.configName || !editForm.providerType) return;
    setSaving(true);
    
    const hasActive = configs.some(c => c.default);
    const isThisActive = editForm.default || !hasActive;
    
    try {
      const res = await fetch('/api/config/llm', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          id: editForm.id,
            configName: editForm.configName,
          providerType: editForm.providerType,
          model: editForm.activeModel || '',
          baseUrl: editForm.baseUrl || '',
          apiKeys: editForm.apiKeys || [],
          isDefault: isThisActive
        })
      });
      if (res.ok) {
        setIsCreatingNew(false);
        setSelectedConfig(editForm.configName);
        await fetchAllConfigs();
      }
    } catch (e) {
      console.error("Failed to save config", e);
      alert('Failed to save settings');
    } finally {
      setSaving(false);
    }
  };

  const deleteConfig = async () => {
    if (!editForm.configName || !confirm('Are you sure you want to delete this profile?')) return;
    try {
      const res = await fetch('/api/config/llm/' + editForm.id, {
        method: 'DELETE'
      });
      if (res.ok) {
        setSelectedConfig('');
        await fetchAllConfigs();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleAddKey = () => {
    const currentKeys = editForm.apiKeys || [];
    setEditForm({ ...editForm, apiKeys: [...currentKeys, { keyName: '', keyValue: '' }] });
  };

  const handleRemoveKey = (index: number) => {
    const currentKeys = [...(editForm.apiKeys || [])];
    currentKeys.splice(index, 1);
    setEditForm({ ...editForm, apiKeys: currentKeys });
  };

  const handleKeyChange = (index: number, field: 'keyName' | 'keyValue' | 'isActive', value: string | boolean) => {
    const currentKeys = [...(editForm.apiKeys || [])];
    currentKeys[index] = { ...currentKeys[index], [field]: value };
    setEditForm({ ...editForm, apiKeys: currentKeys });
  };

  if (loading) return <div style={{ padding: 40, color: '#fff' }}>Loading settings...</div>;

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', overflowY: 'auto', padding: '40px 60px', background: 'var(--bg-app)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 30 }}>
        <Settings size={28} color="var(--accent-cyan)" />
        <h1 style={{ color: '#fff', fontSize: 24, margin: 0, fontWeight: 600 }}>Settings</h1>
      </div>

      <div style={{ display: 'flex', gap: 20, marginBottom: 20, borderBottom: '1px solid var(--border-subtle)', paddingBottom: 10 }}>
        <button onClick={() => setActiveTab('llm')} style={{ background: 'transparent', border: 'none', color: activeTab === 'llm' ? 'var(--accent-cyan)' : 'var(--text-secondary)', fontSize: 16, fontWeight: 600, cursor: 'pointer', borderBottom: activeTab === 'llm' ? '2px solid var(--accent-cyan)' : 'none', paddingBottom: 5 }}>LLM Configuration</button>
        <button onClick={() => setActiveTab('advanced')} style={{ background: 'transparent', border: 'none', color: activeTab === 'advanced' ? 'var(--accent-cyan)' : 'var(--text-secondary)', fontSize: 16, fontWeight: 600, cursor: 'pointer', borderBottom: activeTab === 'advanced' ? '2px solid var(--accent-cyan)' : 'none', paddingBottom: 5 }}>Advanced Preferences</button>
      </div>
      {activeTab === 'llm' && (
      <div style={{ display: 'flex', gap: 24, flex: 1, overflow: 'visible', minHeight: 600 }}>
        
        {/* Sidebar - Provider List */}
        <div style={{ 
          width: 250, 
          display: 'flex', 
          flexDirection: 'column', 
          gap: 8, 
          background: 'rgba(255,255,255,0.02)', 
          border: '1px solid var(--border-subtle)',
          borderRadius: 12,
          padding: 16
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <div style={{ color: 'var(--text-secondary)', fontSize: 12, fontWeight: 600, letterSpacing: '0.5px' }}>
              PROFILES
            </div>
            <button onClick={handleCreateNew} style={{ background: 'transparent', border: 'none', color: 'var(--accent-cyan)', cursor: 'pointer', padding: 4 }}>
              <Plus size={16} />
            </button>
          </div>
          
          {configs.map(configData => {
            const isSelected = selectedConfig === configData.configName && !isCreatingNew;
            const isActive = configData.default;
            
            return (
              <div 
                key={configData.configName}
                onClick={() => handleSelectConfig(configData.configName)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '12px 14px',
                  borderRadius: 8,
                  cursor: 'pointer',
                  background: isSelected ? 'rgba(6, 182, 212, 0.15)' : 'transparent',
                  border: isSelected ? '1px solid rgba(6, 182, 212, 0.3)' : '1px solid transparent',
                  transition: 'var(--transition-fast)'
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <span style={{ 
                    color: isSelected ? 'var(--accent-cyan)' : 'var(--text-primary)', 
                    fontWeight: isSelected ? 600 : 400,
                    fontSize: 14
                  }}>
                    {configData.configName}
                  </span>
                  <span style={{ fontSize: 11, color: 'var(--text-dim)' }}>
                    {configData.providerType.toUpperCase()} | {configData.activeModel || 'default'}
                  </span>
                </div>
                
                {isActive && (
                  <div title="Active Default Provider" style={{ color: 'var(--accent-cyan)' }}>
                    <CheckCircle2 size={16} />
                  </div>
                )}
              </div>
            );
          })}

          {isCreatingNew && (
            <div 
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '12px 14px',
                borderRadius: 8,
                cursor: 'pointer',
                background: 'rgba(6, 182, 212, 0.15)',
                border: '1px solid rgba(6, 182, 212, 0.3)',
                transition: 'var(--transition-fast)'
              }}
            >
              <span style={{ color: 'var(--accent-cyan)', fontWeight: 600, fontSize: 14 }}>
                New Profile
              </span>
            </div>
          )}
        </div>

        {/* Main Panel - Config Editor */}
        <div className="glass-panel" style={{ flex: 1, padding: '30px 40px', borderRadius: 12, display: 'flex', flexDirection: 'column', gap: 24, overflowY: 'auto' }}>
          
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <input
                  value={editForm.configName || ''}
                  onChange={(e) => setEditForm({...editForm, configName: e.target.value})}
                  placeholder="Profile Name"
                  style={{ fontSize: 20, fontWeight: 'bold', background: 'transparent', color: '#fff', border: '1px solid var(--border-subtle)', borderRadius: 6, padding: '4px 8px' }}
                />
                {!isCreatingNew && <span style={{ color: '#fff', fontSize: 20, fontWeight: 'bold' }}>Settings</span>}
              </div>
              <p style={{ color: 'var(--text-secondary)', fontSize: 13, margin: '8px 0 0 0' }}>
                Configure models, base URLs, and API keys for this profile.
              </p>
            </div>
            
            {!isCreatingNew && (
              editForm.default ? (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px', background: 'rgba(16, 185, 129, 0.15)', color: 'var(--accent-emerald)', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                  <CheckCircle2 size={14} /> ACTIVE DEFAULT
                </div>
              ) : (
                <button 
                  onClick={() => handleToggleActive(editForm.configName || '')}
                  style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '6px 12px', background: 'rgba(255,255,255,0.05)', color: 'var(--text-primary)', border: '1px solid var(--border-subtle)', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}
                >
                  <Circle size={14} /> SET AS ACTIVE
                </button>
              )
            )}
          </div>

          <hr style={{ border: 'none', borderTop: '1px solid var(--border-subtle)', margin: 0 }} />

          {/* Provider Type */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <label style={{ color: 'var(--text-secondary)', fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Server size={14} /> PROVIDER TYPE
            </label>
            <select
              value={editForm.providerType || 'gemini'}
              onChange={(e) => setEditForm({...editForm, providerType: e.target.value})}
              style={{
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-subtle)',
                color: '#fff',
                padding: '10px 14px',
                borderRadius: 6,
                fontSize: 14,
                outline: 'none',
                width: '100%',
                maxWidth: 400
              }}
            >
              {PROVIDER_TYPES.map(pt => (
                <option key={pt} value={pt} style={{ background: '#1e1e1e' }}>{pt.toUpperCase()}</option>
              ))}
            </select>
          </div>

          {/* Model */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <label style={{ color: 'var(--text-secondary)', fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Sparkles size={14} /> MODEL IDENTIFIER
            </label>
            <input 
              type="text" 
              value={editForm.activeModel || ''} 
              onChange={(e) => setEditForm({ ...editForm, activeModel: e.target.value })}
              list="model-suggestions"
              placeholder={editForm.providerType === 'gemini' ? "e.g. gemini-3.6-flash" : "Select or type model identifier"}
              style={{
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-subtle)',
                color: '#fff',
                padding: '10px 14px',
                borderRadius: 6,
                fontSize: 14,
                outline: 'none',
                width: '100%',
                maxWidth: 400,
                fontFamily: 'var(--font-mono)'
              }}
            />
            <datalist id="model-suggestions">
              {editForm.providerType === 'openrouter' && (
                <>
                  <option value="google/gemma-4-31b-it:free" />
                  <option value="openrouter/free" />
                  <option value="nvidia/nemotron-3-super-120b-a12b:free" />
                  <option value="google/gemma-4-26b-a4b-it:free" />
                  <option value="nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free" />
                  <option value="anthropic/claude-sonnet-5.5" />
                  <option value="openai/gpt-5.5-pro" />
                  <option value="deepseek/deepseek-v4-pro" />
                  <option value="qwen/qwen3.8-max-prime" />
                </>
              )}
              {editForm.providerType === 'groq' && (
                <>
                  <option value="qwen/qwen3.6-27b" />
                  <option value="meta-llama/llama-4-scout" />
                  <option value="deepseek-v4-flash" />
                  <option value="meta-llama/llama-4-maverick" />
                </>
              )}
              {editForm.providerType === 'gemini' && (
                <>
                  <option value="gemini-3.6-flash" />
                  <option value="gemini-3.1-pro-preview" />
                  <option value="gemini-3.5-flash" />
                </>
              )}
            </datalist>
          </div>

          {/* Base URL */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <label style={{ color: 'var(--text-secondary)', fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
              <Server size={14} /> BASE URL (Optional for Gemini & Local)
            </label>
            <input 
              type="text" 
              value={editForm.baseUrl || ''} 
              onChange={(e) => setEditForm({ ...editForm, baseUrl: e.target.value })}
              placeholder={editForm.providerType === 'local' ? "e.g. http://localhost:8080/v1" : "e.g. https://api.groq.com/openai/v1"}
              style={{
                background: 'rgba(255, 255, 255, 0.05)',
                border: '1px solid var(--border-subtle)',
                color: '#fff',
                padding: '10px 14px',
                borderRadius: 6,
                fontSize: 14,
                outline: 'none',
                width: '100%',
                maxWidth: 400
              }}
            />
          </div>

          {/* API Keys */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <label style={{ color: 'var(--text-secondary)', fontSize: 13, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 6 }}>
                <KeySquare size={14} /> API KEYS (Round-Robin Rotation)
              </label>
              <button 
                onClick={handleAddKey}
                style={{
                  background: 'rgba(6, 182, 212, 0.1)',
                  color: 'var(--accent-cyan)',
                  border: '1px solid rgba(6, 182, 212, 0.2)',
                  padding: '4px 10px',
                  borderRadius: 4,
                  fontSize: 12,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4
                }}
              >
                <Plus size={14} /> Add Key
              </button>
            </div>
            
            {(editForm.apiKeys || []).map((keyEntry, index) => (
              <div key={index} style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                <div style={{ 
                  background: 'rgba(255,255,255,0.02)', 
                  border: '1px solid var(--border-subtle)', 
                  padding: '10px 14px', 
                  borderRadius: 6,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}>
                  <Key size={14} color="var(--text-dim)" />
                </div>
                <input 
                  type="text" 
                  value={keyEntry.keyName || ''} 
                  onChange={(e) => handleKeyChange(index, 'keyName', e.target.value)}
                  placeholder="Optional Label (e.g. Work)"
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
                  type="text" 
                  value={keyEntry.keyValue || ''} 
                  onChange={(e) => handleKeyChange(index, 'keyValue', e.target.value)}
                  placeholder="Enter API Key..."
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
                />
                <button 
                  onClick={() => handleKeyChange(index, 'isActive', keyEntry.isActive === undefined ? false : !keyEntry.isActive)}
                  style={{
                    background: (keyEntry.isActive === undefined || keyEntry.isActive) ? 'rgba(34, 197, 94, 0.1)' : 'rgba(107, 114, 128, 0.1)',
                    color: (keyEntry.isActive === undefined || keyEntry.isActive) ? '#22c55e' : '#9ca3af',
                    border: '1px solid ' + ((keyEntry.isActive === undefined || keyEntry.isActive) ? 'rgba(34, 197, 94, 0.3)' : 'rgba(107, 114, 128, 0.3)'),
                    padding: '8px 14px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 12,
                    fontWeight: 600,
                    minWidth: 80
                  }}
                  title={(keyEntry.isActive === undefined || keyEntry.isActive) ? "Active (Click to disable)" : "Inactive (Click to enable)"}
                >
                  {(keyEntry.isActive === undefined || keyEntry.isActive) ? 'Active' : 'Inactive'}
                </button>
                <button 
                  onClick={() => handleRemoveKey(index)}
                  style={{
                    background: 'rgba(239, 68, 68, 0.1)',
                    color: '#ef4444',
                    border: 'none',
                    padding: '10px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center'
                  }}
                  title="Remove Key"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
            
            {(!editForm.apiKeys || editForm.apiKeys.length === 0) && (
              <div style={{ color: 'var(--text-dim)', fontSize: 13, fontStyle: 'italic', padding: '10px 0' }}>
                No API keys configured. Click 'Add Key' to add one. (Not needed for local endpoints without auth).
              </div>
            )}
          </div>

          {/* Save Action */}
          <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'flex-start', paddingTop: 20, gap: 12 }}>
            <button 
              onClick={saveConfig}
              disabled={saving}
              style={{
                background: 'var(--accent-cyan)',
                color: '#07090e',
                border: 'none',
                padding: '10px 24px',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: 600,
                cursor: saving ? 'not-allowed' : 'pointer',
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                opacity: saving ? 0.7 : 1,
                boxShadow: '0 0 15px rgba(6, 182, 212, 0.3)'
              }}
            >
              <Save size={16} />
              {saving ? 'Saving...' : 'Save Configuration'}
            </button>
            {!isCreatingNew && (
              <button 
                onClick={deleteConfig}
                disabled={saving}
                style={{
                  background: 'rgba(239, 68, 68, 0.1)',
                  color: '#ef4444',
                  border: '1px solid rgba(239, 68, 68, 0.2)',
                  padding: '10px 24px',
                  borderRadius: 6,
                  fontSize: 14,
                  fontWeight: 600,
                  cursor: saving ? 'not-allowed' : 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  opacity: saving ? 0.7 : 1
                }}
              >
                <Trash2 size={16} />
                Delete Profile
              </button>
            )}
          </div>

        </div>
      </div>
      )}
      {activeTab === 'advanced' && <AdvancedSettings />}
    </div>
  );
};



