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
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width" />
        <meta name="theme-color" content="#000000" />
        <link rel="manifest" href="/manifest.json" />
        <link rel="stylesheet" type="text/css" href="/applications.css" />
        <link
          rel="preload"
          as="font"
          href="https://sp-bootstrap.global.ssl.fastly.net/8.0.0/fonts/circular-medium.woff2"
          type="font/woff2"
          crossOrigin="anonymous"
        />
        <link
          rel="preload"
          as="font"
          href="https://sp-bootstrap.global.ssl.fastly.net/8.0.0/fonts/circular-bold.woff2"
          type="font/woff2"
          crossOrigin="anonymous"
        />
        <link
          rel="preload"
          as="font"
          href="https://sp-bootstrap.global.ssl.fastly.net/8.0.0/fonts/circular-book.woff2"
          type="font/woff2"
          crossOrigin="anonymous"
        />
        <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css" />
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/octicons/3.5.0/octicons.css" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/icon?family=Material+Icons" />
        <link rel="shortcut icon" href="/favicon.png"></link>
        <RouterHead />
      </head>
      <body>
        <RouterOutlet />
        <ServiceWorkerRegister />
        <script type="text/javascript" src="/d3.v6.min.js" />
        <script type="text/javascript" src="/timeline.js" />
        <script type="text/javascript" src="/spive.js" />
      </body>
    </QwikCityProvider>
  );
});
