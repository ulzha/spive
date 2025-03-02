import { component$ } from "@builder.io/qwik";
import { QwikCityProvider, RouterOutlet, ServiceWorkerRegister } from "@builder.io/qwik-city";
import { RouterHead } from "./components/router-head/router-head";

import "./global.css";

export default component$(() => {
  /**
   * The root of a QwikCity site always start with the <QwikCityProvider> component,
   * immediately followed by the document's <head> and <body>.
   *
   * Dont remove the `<head>` and `<body>` elements.
   */

  return (
    <QwikCityProvider>
      <head>
        <meta name="viewport" content="width=device-width" />
        <meta name="theme-color" content="#000000" />
        <link rel="manifest" href="/manifest.json" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons" />
        <link rel="icon" href="/favicon.png" />
        <link rel="preload" type="font/woff2" href="/fonts/LINESeedSans_W_Rg.woff2" as="font" crossOrigin="anonymous" />
        <link rel="preload" type="font/woff2" href="/fonts/LINESeedSans_W_Bd.woff2" as="font" crossOrigin="anonymous" />
        <RouterHead />
      </head>
      <body>
        <RouterOutlet />
        <ServiceWorkerRegister />
        <script type="text/javascript" src="/d3.v6.min.js" />
        <script type="text/javascript" src="/bigsvg.js" />
        <script type="text/javascript" src="/timeline.js" />
      </body>
    </QwikCityProvider>
  );
});
