/** @jsxImportSource react */

import { Card } from "@mui/material";
import HelpButton from "~/components/nav/support/help";
import ImgArmeria from "~/media/icons/armeria.svg";
import ImgD3 from "~/media/icons/d3.svg";
import ImgGitHub from "~/media/icons/github.svg";
import ImgQwik from "~/media/icons/qwik.svg";
import { qwikify$ } from "@builder.io/qwik-react";
import styles from "./nav.module.css";

export default qwikify$((props) => (
  <div className="padding">
    <div className={styles.credits}>
      <a href="https://github.com/ulzha/spive" title="GitHub" target="_blank">
        <ImgGitHub />
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
        <ImgArmeria />
      </a>
      <a href="https://qwik.builder.io/" title="Qwik" target="_blank">
        <ImgQwik />
      </a>
      <a href="https://d3js.org/" title="D3" target="_blank">
        <ImgD3 />
      </a>
    </div>
  </div>
));
