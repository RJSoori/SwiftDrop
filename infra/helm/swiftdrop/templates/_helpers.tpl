{{/* env entry read from swiftdrop-secrets. Call with (list root envName secretKey). */}}
{{- define "swiftdrop.secretEnv" -}}
{{- $root := index . 0 -}}
- name: {{ index . 1 }}
  valueFrom:
    secretKeyRef:
      name: swiftdrop-secrets
      key: {{ index . 2 }}
      {{- if has (index . 2) $root.Values.optionalSecrets }}
      optional: true
      {{- end }}
{{- end }}

{{/* Init container that waits until each host:port accepts connections. Call with a list of host:port. */}}
{{- define "swiftdrop.waitFor" -}}
{{- if . -}}
initContainers:
  - name: wait-for-deps
    image: busybox:1.37
    command: ["sh", "-c", {{ printf "for d in %s; do until nc -z -w 2 ${d%%:*} ${d##*:}; do echo waiting for $d; sleep 2; done; done" (join " " .) | quote }}]
{{- end }}
{{- end }}
