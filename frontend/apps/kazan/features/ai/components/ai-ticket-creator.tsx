'use client';

import { useState } from 'react';
import { useTranslations } from 'next-intl';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Sparkles, Loader2, CheckCircle2, AlertCircle } from 'lucide-react';
import { TicketTypeBadge } from '@/features/tickets/components/ticket-type-badge';
import { TicketPriorityIcon } from '@/features/tickets/components/ticket-priority-icon';
import {
  useAiAvailability,
  useAnalyzeWithAi,
  useCreateFromSuggestions,
} from '@/features/ai/hooks/use-ai';
import type { TicketType, TicketPriority } from '@/features/tickets/schemas';

interface AITicketCreatorProps {
  boardId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

type Stage = 'input' | 'review' | 'success';

interface Suggestion {
  title: string;
  description: string;
  type: TicketType;
  priority: TicketPriority;
  selected: boolean;
}

export function AITicketCreator({
  boardId,
  open,
  onOpenChange,
  onSuccess,
}: AITicketCreatorProps) {
  const t = useTranslations();
  const [stage, setStage] = useState<Stage>('input');
  const [userInput, setUserInput] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [reasoning, setReasoning] = useState('');
  const [error, setError] = useState('');

  const { data: aiAvailabilityData } = useAiAvailability(boardId, {
    enabled: open,
  });
  const aiStatus = aiAvailabilityData?.data
    ? { available: !!aiAvailabilityData.data.available }
    : undefined;

  const analyzeMutation = useAnalyzeWithAi();
  const createMutation = useCreateFromSuggestions();

  const resetState = () => {
    setStage('input');
    setUserInput('');
    setSuggestions([]);
    setReasoning('');
    setError('');
  };

  const handleAnalyze = () => {
    setError('');
    analyzeMutation.mutate(
      { boardId, data: { userInput } },
      {
        onSuccess: (response) => {
          const result = response.data;
          setSuggestions(
            result?.suggestions?.map((s) => ({
              title: s.title,
              description: s.description,
              type: s.type as TicketType,
              priority: s.priority as TicketPriority,
              selected: true,
            })) ?? [],
          );
          setReasoning(result?.reasoning ?? '');
          setStage('review');
        },
        onError: () => {
          setError(t('ai.ticketCreator.analyzeError'));
        },
      },
    );
  };

  const handleCreate = () => {
    const selected = suggestions.filter((s) => s.selected);
    if (selected.length === 0) {
      setError(t('ai.ticketCreator.selectAtLeastOne'));
      return;
    }
    createMutation.mutate(
      {
        boardId,
        data: {
          suggestions: selected.map(({ selected: _, ...s }) => s),
          addToBacklog: true,
        },
      },
      {
        onSuccess: () => {
          setStage('success');
          onSuccess?.();
        },
        onError: () => {
          setError(t('ai.ticketCreator.createError'));
        },
      },
    );
  };

  const toggleSuggestion = (index: number) => {
    setSuggestions((prev) =>
      prev.map((s, i) => (i === index ? { ...s, selected: !s.selected } : s))
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        onOpenChange(o);
        if (!o) resetState();
      }}
    >
      <DialogContent className="sm:max-w-[600px] max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="text-primary h-5 w-5" />
            {t('ai.ticketCreator.title')}
          </DialogTitle>
          <DialogDescription>
            {t('ai.ticketCreator.description')}
          </DialogDescription>
        </DialogHeader>

        {!aiStatus?.available && (
          <div className="p-4 bg-amber-50 border border-amber-200 rounded-lg">
            <div className="flex items-center gap-2 text-amber-800">
              <AlertCircle className="h-5 w-5" />
              <span className="font-medium">{t('ai.ticketCreator.notConfigured')}</span>
            </div>
            <p className="text-sm text-amber-700 mt-1">
              {t('ai.ticketCreator.notConfiguredDescription')}
            </p>
          </div>
        )}

        {error && (
          <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
            {error}
          </div>
        )}

        {/* Stage: Input */}
        {stage === 'input' && aiStatus?.available && (
          <div className="space-y-4">
            <Textarea
              placeholder={t('ai.ticketCreator.inputPlaceholder')}
              value={userInput}
              onChange={(e) => setUserInput(e.target.value)}
              rows={6}
              className="resize-none"
            />
            <DialogFooter>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                {t('common.cancel')}
              </Button>
              <Button
                onClick={handleAnalyze}
                disabled={!userInput.trim() || analyzeMutation.isPending}
                className="bg-primary hover:bg-primary/90 gap-2"
              >
                {analyzeMutation.isPending ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {t('ai.ticketCreator.analyzing')}
                  </>
                ) : (
                  <>
                    <Sparkles className="h-4 w-4" />
                    {t('ai.ticketCreator.analyzeButton')}
                  </>
                )}
              </Button>
            </DialogFooter>
          </div>
        )}

        {/* Stage: Review */}
        {stage === 'review' && (
          <div className="space-y-4">
            {reasoning && (
              <div className="p-3 bg-gray-50 rounded-lg text-sm text-gray-600">
                <strong>{t('ai.ticketCreator.aiAnalysis')}:</strong> {reasoning}
              </div>
            )}

            <div className="space-y-3">
              {suggestions.map((suggestion, index) => (
                <div
                  key={index}
                  className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                    suggestion.selected
                      ? 'border-primary bg-korur-blue-accent/30 dark:bg-korur-slate/30'
                      : 'border-gray-200'
                  }`}
                  onClick={() => toggleSuggestion(index)}
                >
                  <div className="flex items-start gap-3">
                    <Checkbox checked={suggestion.selected} className="mt-1" />
                    <div className="flex-1 space-y-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-medium">{suggestion.title}</span>
                        <TicketTypeBadge type={suggestion.type} />
                        <TicketPriorityIcon priority={suggestion.priority} />
                      </div>
                      <p className="text-sm text-gray-600">
                        {suggestion.description}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button variant="outline" onClick={() => setStage('input')}>
                {t('ai.ticketCreator.editRequest')}
              </Button>
              <Button
                onClick={handleCreate}
                disabled={
                  createMutation.isPending ||
                  !suggestions.some((s) => s.selected)
                }
                className="bg-primary hover:bg-primary/90 gap-2"
              >
                {createMutation.isPending ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {t('ai.ticketCreator.creating')}
                  </>
                ) : (
                  <>
                    {t('ai.ticketCreator.createButton', { count: suggestions.filter((s) => s.selected).length })}
                  </>
                )}
              </Button>
            </DialogFooter>
          </div>
        )}

        {/* Stage: Success */}
        {stage === 'success' && (
          <div className="py-8 text-center">
            <CheckCircle2 className="h-12 w-12 text-green-500 mx-auto mb-3" />
            <p className="text-lg font-medium">{t('ai.ticketCreator.success')}</p>
            <p className="text-sm text-gray-500">{t('ai.ticketCreator.addedToBacklog')}</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
