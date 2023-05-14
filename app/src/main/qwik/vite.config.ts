import { defineConfig } from "vite";
import { qwikVite } from "@builder.io/qwik/optimizer";
import { qwikCity } from "@builder.io/qwik-city/vite";
import tsconfigPaths from "vite-tsconfig-paths";
import { qwikReact } from "@builder.io/qwik-react/vite";

export default defineConfig(() => {
  return {
    plugins: [qwikCity(), qwikVite(), tsconfigPaths(), qwikReact()],
    preview: {
      headers: {
        "Cache-Control": "public, max-age=600",
      },
    },
    css: {
      modules: {
        scopeBehaviour: "global",
      },
      postcss: {
        plugins: [require("postcss-nested")],
      },
    },
    // ssr: {
    //   noExternal: [
    //     'express',
    //     '@mui/x-date-pickers',
    //     '@mui/material',
    //     '@mui/base',
    //     '@uiw/react-markdown-preview',
    //     '@uiw/react-md-editor',
    //   ],
    // },
    // build: {
    //   rollupOptions: {
    //     output: {
    //       format: 'es',
    //     }

    //   },
    //   minify: false,
    // },
  };
});
