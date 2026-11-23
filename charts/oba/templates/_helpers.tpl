{{/*
The components, in the order they render.
*/}}
{{- define "oba.components" -}}
api kazan kaya kalkan otak ulak account
{{- end }}

{{- define "oba.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Prefix of every object name. Truncated so that "<fullname>-<component>-service"
stays within the 63 characters of a DNS label.
*/}}
{{- define "oba.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 40 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 40 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 40 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "oba.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Name of a component's Deployment, ServiceAccount, Ingress and PDB.
Takes a dict with "root" (the top-level context) and "name" (the component).
*/}}
{{- define "oba.componentName" -}}
{{- printf "%s-%s" (include "oba.fullname" .root) .name }}
{{- end }}

{{/*
Selector of a component's pods. A Deployment selector cannot change after
creation, so this is the single "app" label and nothing else.
*/}}
{{- define "oba.selectorLabels" -}}
app: {{ include "oba.componentName" . }}
{{- end }}

{{- define "oba.labels" -}}
{{ include "oba.selectorLabels" . }}
helm.sh/chart: {{ include "oba.chart" .root }}
app.kubernetes.io/name: {{ include "oba.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .name }}
app.kubernetes.io/part-of: oba
app.kubernetes.io/version: {{ .root.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
{{- with .root.Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{- define "oba.image" -}}
{{- $c := index .root.Values .name }}
{{- printf "%s:%s" $c.image.repository (default .root.Chart.AppVersion ($c.image.tag | toString)) }}
{{- end }}
