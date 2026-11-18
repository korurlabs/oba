// lucide-react ships no types for its per-icon entry points, which the
// editor's WebView bundle imports so that only the icons it shows are bundled
// instead of the whole icon set.
declare module "lucide-react/icons/*" {
  const icon: import("lucide-react").LucideIcon;
  export default icon;
}
