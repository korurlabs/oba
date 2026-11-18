// Package jsonedit changes single keys of a JSON object and keeps every other key, its order
// and its value as written, so that editing a user's settings file never reorders or rewrites
// what the CLI does not own.
package jsonedit

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
)

// Object is a JSON object with its keys in file order and each value as raw JSON.
type Object struct {
	keys   []string
	values map[string]json.RawMessage
}

// NewObject is an empty object.
func NewObject() *Object {
	return &Object{values: map[string]json.RawMessage{}}
}

// Parse reads a JSON object. Empty or whitespace-only input is an empty object. A duplicate key
// is refused, because writing the file back would silently drop one of the values.
func Parse(data []byte) (*Object, error) {
	obj := NewObject()
	if len(bytes.TrimSpace(data)) == 0 {
		return obj, nil
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	token, err := decoder.Token()
	if err != nil {
		return nil, fmt.Errorf("not valid JSON: %w", err)
	}
	if delim, ok := token.(json.Delim); !ok || delim != '{' {
		return nil, errors.New("not a JSON object")
	}
	for decoder.More() {
		keyToken, err := decoder.Token()
		if err != nil {
			return nil, fmt.Errorf("not valid JSON: %w", err)
		}
		key, ok := keyToken.(string)
		if !ok {
			return nil, errors.New("not valid JSON: an object key is not a string")
		}
		var value json.RawMessage
		if err := decoder.Decode(&value); err != nil {
			return nil, fmt.Errorf("not valid JSON at %q: %w", key, err)
		}
		if _, dup := obj.values[key]; dup {
			return nil, fmt.Errorf("the key %q appears twice", key)
		}
		obj.keys = append(obj.keys, key)
		obj.values[key] = value
	}
	if _, err := decoder.Token(); err != nil {
		return nil, fmt.Errorf("not valid JSON: %w", err)
	}
	if _, err := decoder.Token(); err == nil {
		return nil, errors.New("not valid JSON: data after the object")
	}
	return obj, nil
}

// Get is the raw value of key.
func (o *Object) Get(key string) (json.RawMessage, bool) {
	value, ok := o.values[key]
	return value, ok
}

// Object reads the value of key as an object; a missing key is a new empty object.
func (o *Object) Object(key string) (*Object, error) {
	raw, ok := o.values[key]
	if !ok {
		return NewObject(), nil
	}
	child, err := Parse(raw)
	if err != nil {
		return nil, fmt.Errorf("%q: %w", key, err)
	}
	return child, nil
}

// Set replaces the value of key in place, or adds it at the end.
func (o *Object) Set(key string, value any) error {
	raw, err := toRaw(value)
	if err != nil {
		return fmt.Errorf("encode %q: %w", key, err)
	}
	if _, ok := o.values[key]; !ok {
		o.keys = append(o.keys, key)
	}
	o.values[key] = raw
	return nil
}

// Delete removes key and keeps the order of the other keys; a missing key changes nothing.
func (o *Object) Delete(key string) {
	if _, ok := o.values[key]; !ok {
		return
	}
	delete(o.values, key)
	for i, existing := range o.keys {
		if existing == key {
			o.keys = append(o.keys[:i], o.keys[i+1:]...)
			return
		}
	}
}

// Len is the number of keys.
func (o *Object) Len() int {
	return len(o.keys)
}

func toRaw(value any) (json.RawMessage, error) {
	switch v := value.(type) {
	case *Object:
		return v.compact()
	case json.RawMessage:
		return v, nil
	default:
		// Settings hold shell commands and permission rules; & < > stay readable as written.
		var buf bytes.Buffer
		encoder := json.NewEncoder(&buf)
		encoder.SetEscapeHTML(false)
		if err := encoder.Encode(v); err != nil {
			return nil, err
		}
		return bytes.TrimRight(buf.Bytes(), "\n"), nil
	}
}

func (o *Object) compact() (json.RawMessage, error) {
	var buf bytes.Buffer
	buf.WriteByte('{')
	for i, key := range o.keys {
		if i > 0 {
			buf.WriteByte(',')
		}
		encodedKey, err := toRaw(key)
		if err != nil {
			return nil, err
		}
		buf.Write(encodedKey)
		buf.WriteByte(':')
		if err := json.Compact(&buf, o.values[key]); err != nil {
			return nil, fmt.Errorf("%q: %w", key, err)
		}
	}
	buf.WriteByte('}')
	return buf.Bytes(), nil
}

// Marshal writes the object indented by two spaces, with a final newline.
func (o *Object) Marshal() ([]byte, error) {
	compact, err := o.compact()
	if err != nil {
		return nil, err
	}
	var out bytes.Buffer
	if err := json.Indent(&out, compact, "", "  "); err != nil {
		return nil, err
	}
	out.WriteByte('\n')
	return out.Bytes(), nil
}
