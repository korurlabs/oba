"use client";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Check, X } from "lucide-react";
import { useTranslations } from "next-intl";
import { ColorPicker, type ColorOption } from "@/components/shared/color-picker";

/** The palette of labels and projects, brighter than the board palette. */
export const PRESET_COLORS: readonly ColorOption[] = [
  { name: "Red", value: "#EF4444" },
  { name: "Orange", value: "#F97316" },
  { name: "Yellow", value: "#EAB308" },
  { name: "Green", value: "#22C55E" },
  { name: "Cyan", value: "#06B6D4" },
  { name: "Blue", value: "#3B82F6" },
  { name: "Violet", value: "#8B5CF6" },
  { name: "Pink", value: "#EC4899" },
  { name: "Gray", value: "#6B7280" },
  { name: "Stone", value: "#78716C" },
];

export interface InlineFormProps {
  name: string;
  color: string;
  onNameChange: (value: string) => void;
  onColorChange: (value: string) => void;
  onSave: () => void;
  onCancel: () => void;
  isSaving: boolean;
  namePlaceholder: string;
}

export function InlineForm({
  name,
  color,
  onNameChange,
  onColorChange,
  onSave,
  onCancel,
  isSaving,
  namePlaceholder,
}: InlineFormProps) {
  const t = useTranslations("common.colorPicker");
  return (
    <div className="space-y-3 rounded-lg border p-3">
      <div className="flex items-center gap-2">
        <Input
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
          placeholder={namePlaceholder}
          className="h-8 flex-1 text-sm"
          autoFocus
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSaveWithPrevent(e, onSave);
            if (e.key === "Escape") onCancel();
          }}
          disabled={isSaving}
        />
        <Button
          size="sm"
          variant="ghost"
          className="h-8 w-8 p-0"
          onClick={onSave}
          disabled={!name.trim() || isSaving}
        >
          <Check className="h-4 w-4" />
        </Button>
        <Button
          size="sm"
          variant="ghost"
          className="h-8 w-8 p-0"
          onClick={onCancel}
          disabled={isSaving}
        >
          <X className="h-4 w-4" />
        </Button>
      </div>
      <ColorPicker
        label={t("label")}
        hideLabel
        variant="dot"
        value={color}
        onChange={onColorChange}
        options={PRESET_COLORS}
        disabled={isSaving}
      />
    </div>
  );
}

function handleSaveWithPrevent(e: React.KeyboardEvent, onSave: () => void) {
  e.preventDefault();
  onSave();
}
