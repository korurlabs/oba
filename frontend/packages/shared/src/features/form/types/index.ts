import { Control, FieldValues, Path } from "react-hook-form";

export type FormfieldProps<TFieldValues extends FieldValues> = {
  name: Path<TFieldValues>;
  label?: string;
  description?: string;
  control: Control<TFieldValues>;
  required?: boolean;
  tooltipText?: string;
};
