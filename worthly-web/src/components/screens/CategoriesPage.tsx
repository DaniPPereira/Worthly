"use client";

import Link from "next/link";
import { useEffect, useState, type CSSProperties } from "react";
import { apiGet, apiSend } from "@/lib/api";
import { useAppData } from "@/lib/app-data";
import { customCategories, defaultParentId, parentCategories } from "@/lib/categories";
import { formatMerchantRule, splitMatchPhrases } from "@/lib/merchant-rule";
import type { CategorizationRule, Category, TransactionPage } from "@/lib/types";

export function CategoriesPage() {
  const { categories, refresh } = useAppData();
  const [rules, setRules] = useState<CategorizationRule[]>([]);
  const [uncategorized, setUncategorized] = useState(0);
  const [saving, setSaving] = useState(false);
  const [newLabel, setNewLabel] = useState("");
  const [newParentId, setNewParentId] = useState(defaultParentId(categories));
  const [newPhrases, setNewPhrases] = useState("");
  const [phraseCategoryId, setPhraseCategoryId] = useState("");
  const [phraseValue, setPhraseValue] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editLabel, setEditLabel] = useState("");
  const [editParentId, setEditParentId] = useState("");
  const [showNewCategory, setShowNewCategory] = useState(false);

  useEffect(() => {
    const uncategorizedId = categories.find((item) => item.code === "uncategorized")?.id;
    void apiGet<CategorizationRule[]>("/categorization-rules").then(setRules).catch(() => setRules([]));
    if (uncategorizedId) {
      void apiGet<TransactionPage>(`/transactions?categoryId=${uncategorizedId}&size=1`)
        .then((page) => setUncategorized(page.total))
        .catch(() => setUncategorized(0));
    }
  }, [categories]);

  useEffect(() => {
    setNewParentId((current) => current || defaultParentId(categories));
    setPhraseCategoryId((current) => current || customCategories(categories)[0]?.id || defaultParentId(categories));
  }, [categories]);

  async function deleteRule(id: string) {
    await apiSend("DELETE", `/categorization-rules/${id}`);
    setRules((current) => current.filter((item) => item.id !== id));
  }

  async function addDescriptionRules(categoryId: string, phrases: string[]) {
    const createdList: CategorizationRule[] = [];
    for (const phrase of phrases) {
      const created = await apiSend<CategorizationRule>("POST", "/categorization-rules", {
        priority: 0,
        field: "DESCRIPTION",
        operator: "CONTAINS",
        matchValue: phrase,
        targetCategoryId: categoryId,
      });
      if (created) {
        createdList.push(created);
      }
    }
    setRules((current) => {
      const ids = new Set(createdList.map((item) => item.id));
      return [...current.filter((item) => !ids.has(item.id)), ...createdList];
    });
  }

  async function createCategory() {
    const label = newLabel.trim();
    if (!label || !newParentId) {
      return;
    }
    setSaving(true);
    try {
      const created = await apiSend<Category>("POST", "/categories", { label, parentId: newParentId });
      const phrases = splitMatchPhrases(newPhrases);
      if (created && phrases.length > 0) {
        await addDescriptionRules(created.id, phrases);
      }
      setNewLabel("");
      setNewPhrases("");
      setShowNewCategory(false);
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  async function addDescription() {
    const phrases = splitMatchPhrases(phraseValue);
    if (!phraseCategoryId || phrases.length === 0) {
      return;
    }
    setSaving(true);
    try {
      await addDescriptionRules(phraseCategoryId, phrases);
      setPhraseValue("");
    } finally {
      setSaving(false);
    }
  }

  function startEdit(item: Category) {
    setEditingId(item.id);
    setEditLabel(item.label);
    setEditParentId(item.parentId ?? defaultParentId(categories));
  }

  async function saveEdit() {
    if (!editingId || !editLabel.trim()) {
      return;
    }
    setSaving(true);
    try {
      await apiSend("PATCH", `/categories/${editingId}`, { label: editLabel.trim(), parentId: editParentId || null });
      setEditingId(null);
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  async function deleteCategory(item: Category) {
    if (!window.confirm(`Delete “${item.label}”? Transactions in this category become Uncategorized, and its rules are removed.`)) {
      return;
    }
    setSaving(true);
    try {
      await apiSend("DELETE", `/categories/${item.id}`);
      if (editingId === item.id) {
        setEditingId(null);
      }
      if (phraseCategoryId === item.id) {
        setPhraseCategoryId(defaultParentId(categories));
      }
      setRules((current) => current.filter((rule) => rule.targetCategoryId !== item.id));
      await refresh();
    } finally {
      setSaving(false);
    }
  }

  const custom = customCategories(categories);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 720 }}>
      <div className="card" style={{ overflow: "hidden" }}>
        <div className="label" style={{ padding: "15px 20px 13px", borderBottom: "1px solid rgba(19,26,25,.07)" }}>
          Categories
        </div>
        {uncategorized > 0 ? (
          <Link href={`/transactions?categoryId=${categories.find((item) => item.code === "uncategorized")?.id ?? ""}`} style={{ textDecoration: "none", color: "inherit" }}>
            <div style={rowStyle}>
              <span>
                <span style={{ display: "block", fontWeight: 500, fontSize: 13.5, color: "var(--warn)" }}>Uncategorized</span>
                <span style={{ display: "block", fontSize: 11.5, color: "var(--faint)", marginTop: 2 }}>Open the transaction list</span>
              </span>
              <span className="mono" style={{ fontSize: 12.5, fontWeight: 500, color: "var(--warn)" }}>
                {uncategorized}
              </span>
            </div>
          </Link>
        ) : null}
        {custom.map((item) => {
          const parent = categories.find((category) => category.id === item.parentId);
          const editing = editingId === item.id;
          return (
            <div key={item.id} className="settings-row" style={listRow}>
              {editing ? (
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", width: "100%" }}>
                  <input value={editLabel} onChange={(event) => setEditLabel(event.target.value)} maxLength={80} style={{ ...inputStyle, flex: "1 1 140px", width: "auto" }} />
                  <select className="mono" value={editParentId} onChange={(event) => setEditParentId(event.target.value)} style={{ ...selectStyle, flex: "1 1 140px" }}>
                    {parentCategories(categories).map((option) => (
                      <option key={option.id} value={option.id}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                  <button type="button" className="btn btn-ghost" style={{ height: 32 }} disabled={saving} onClick={() => setEditingId(null)}>
                    Cancel
                  </button>
                  <button type="button" className="btn btn-primary" style={{ height: 32 }} disabled={saving || !editLabel.trim()} onClick={() => void saveEdit()}>
                    Save
                  </button>
                </div>
              ) : (
                <>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 500, fontSize: 13.5 }}>{item.label}</div>
                    <div style={{ fontSize: 11.5, color: "var(--faint)", marginTop: 1 }}>{parent?.label ?? "Other expense"}</div>
                  </div>
                  <button type="button" className="btn btn-ghost" style={quietBtn} disabled={saving} onClick={() => startEdit(item)}>
                    Edit
                  </button>
                  <button type="button" className="btn btn-danger" style={quietBtn} disabled={saving} onClick={() => void deleteCategory(item)}>
                    Delete
                  </button>
                </>
              )}
            </div>
          );
        })}
        {showNewCategory ? (
          <div style={composer}>
            <div style={{ fontWeight: 500, fontSize: 13, marginBottom: 8 }}>New category</div>
            <div style={{ display: "flex", gap: 8 }}>
              <input
                value={newLabel}
                onChange={(event) => setNewLabel(event.target.value)}
                maxLength={80}
                placeholder="Name, e.g. Pets"
                style={{ ...inputStyle, flex: 1, width: "auto" }}
              />
              <select className="mono" value={newParentId} onChange={(event) => setNewParentId(event.target.value)} style={selectStyle}>
                {parentCategories(categories).map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
              </select>
            </div>
            <input
              value={newPhrases}
              onChange={(event) => setNewPhrases(event.target.value)}
              placeholder="Optional bank phrases, comma or line separated"
              style={{ ...inputStyle, marginTop: 8 }}
            />
            <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 10 }}>
              <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => setShowNewCategory(false)}>
                Cancel
              </button>
              <button type="button" className="btn btn-primary" style={{ height: 32 }} disabled={saving || !newLabel.trim() || !newParentId} onClick={() => void createCategory()}>
                Create
              </button>
            </div>
          </div>
        ) : (
          <div style={{ padding: "8px 20px 12px" }}>
            <button type="button" className="btn btn-ghost" style={{ height: 32 }} onClick={() => setShowNewCategory(true)}>
              + New category
            </button>
          </div>
        )}
      </div>

      <div className="card" style={{ overflow: "hidden" }}>
        <div className="label" style={{ padding: "15px 20px 13px", borderBottom: "1px solid rgba(19,26,25,.07)" }}>
          Rules
        </div>
        <div style={composer}>
          <div style={{ fontWeight: 500, fontSize: 13 }}>Add a description</div>
          <div className="muted" style={{ margin: "2px 0 8px" }}>
            When the bank memo contains this phrase, assign the category.
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <select
              className="mono"
              value={phraseCategoryId}
              onChange={(event) => setPhraseCategoryId(event.target.value)}
              style={{ ...selectStyle, flex: "1 1 140px" }}
            >
              {categories
                .filter((item) => item.code !== "uncategorized")
                .map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.label}
                  </option>
                ))}
            </select>
            <input
              value={phraseValue}
              onChange={(event) => setPhraseValue(event.target.value)}
              maxLength={200}
              placeholder="Contains, e.g. veterinario"
              style={{ ...inputStyle, flex: 1, width: "auto" }}
            />
            <button type="button" className="btn btn-primary" style={{ height: 34 }} disabled={saving || !phraseCategoryId || splitMatchPhrases(phraseValue).length === 0} onClick={() => void addDescription()}>
              Add
            </button>
          </div>
        </div>
        {rules.map((rule) => {
          const label = categories.find((item) => item.id === rule.targetCategoryId)?.label ?? "category";
          return (
            <div key={rule.id} className="settings-row" style={listRow}>
              <div style={{ flex: 1, minWidth: 0, fontSize: 13 }}>{formatMerchantRule(rule, label)}</div>
              <button type="button" className="btn btn-ghost" style={quietBtn} onClick={() => void deleteRule(rule.id)}>
                Delete
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

const selectStyle: CSSProperties = {
  border: "1px solid rgba(19,26,25,.12)",
  borderRadius: 8,
  padding: "6px 8px",
  background: "#fff",
  fontSize: 12.5,
};

const inputStyle: CSSProperties = {
  ...selectStyle,
  font: "500 13px var(--font-sans)",
  width: "100%",
};

const listRow: CSSProperties = {
  padding: "9px 20px",
  borderBottom: "1px solid rgba(19,26,25,.05)",
  display: "flex",
  alignItems: "center",
  gap: 10,
};

const composer: CSSProperties = {
  margin: "4px 16px 14px",
  padding: "12px 14px",
  background: "#FBF9F5",
  border: "1px solid rgba(19,26,25,.07)",
  borderRadius: 12,
};

const quietBtn: CSSProperties = {
  height: 30,
  padding: "0 10px",
};

const rowStyle: CSSProperties = {
  padding: "14px 20px",
  borderBottom: "1px solid rgba(19,26,25,.05)",
  display: "flex",
  alignItems: "center",
  gap: 14,
};
