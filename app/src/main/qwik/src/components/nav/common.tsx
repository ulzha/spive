/** @jsxImportSource react */

import { Card } from "@mui/material";
import HelpButton from "~/components/nav/support/help";
import { qwikify$ } from "@builder.io/qwik-react";
import styles from "./nav.module.css";

interface CommonCardProps {
  titleText: string;
  class: string;
  children?: any;
};

export default qwikify$((props: CommonCardProps) => (
  <div className="padding">
    <div className={styles.credits}>
      <a href="https://github.com/ulzha/spive" title="GitHub" target="_blank">
        <img src="/icons/github.svg" />
      </a>
    </div>
    <div className="titlebar">
      <h2>{props.titleText}</h2>
      <HelpButton />
    </div>
    <div className="passepartout">
      <Card elevation={1} className={props.class}>
        {props.children}
      </Card>
    </div>
    <div className={styles.credits}>
      Built with:
      <a href="https://armeria.dev/" title="Armeria" target="_blank">
        <img src="/icons/armeria.svg" />
      </a>
      <a href="https://qwik.builder.io/" title="Qwik" target="_blank">
        <img src="/icons/qwik.svg" />
      </a>
      <a href="https://d3js.org/" title="D3" target="_blank">
        <img src="/icons/d3.svg" />
      </a>
    </div>
  </div>
));
