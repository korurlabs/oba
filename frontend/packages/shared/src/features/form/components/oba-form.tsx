"use client";

import { cn } from "@oba/ui/lib/utils";
import { zodResolver } from "@hookform/resolvers/zod";
import { FormEvent, ReactNode, useEffect } from "react";
import {
  DefaultValues,
  FieldValues,
  FormProvider,
  SubmitErrorHandler,
  UseFormProps,
  UseFormReturn,
  useForm,
} from "react-hook-form";
import { ZodSchema } from "zod";

type ObaFormProps<T extends FieldValues> = {
  children: ReactNode;
  schema: ZodSchema<T, T>;
  onSubmit: (
    data: T,
    form: UseFormReturn<T>,
    event?: FormEvent<HTMLFormElement>
  ) => void | Promise<void>;
  onError?: SubmitErrorHandler<T>;
  mode?: UseFormProps<T>["mode"];
  reValidateMode?: UseFormProps<T>["reValidateMode"];
  values?: UseFormProps<T>["values"];
  defaultValues?: DefaultValues<T>;
  className?: string;
  //   readOnly?: boolean;
};

export const ObaForm = <T extends FieldValues>({
  children,
  onSubmit,
  schema,
  reValidateMode,
  defaultValues,
  mode,
  values,
  onError,
  className,
}: ObaFormProps<T>) => {
  const form = useForm<T>({
    mode,
    values,
    defaultValues,
    reValidateMode,
    resolver: zodResolver(schema),
  });


  useEffect(() => {
    if (defaultValues) {
      form.reset(defaultValues);
    }
  }, [JSON.stringify(defaultValues)]);

  return (
    <FormProvider {...form}>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          event.stopPropagation();
          form.handleSubmit((data) => onSubmit(data, form, event), onError)(event);
        }}
        className={cn(className)}
      >
        {children}
      </form>
    </FormProvider>
  );
};
