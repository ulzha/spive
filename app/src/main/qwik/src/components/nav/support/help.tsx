/** @jsxImportSource react */

import { Button } from "@mui/material";
import { Help } from "@mui/icons-material";

export default function HelpButton() {
  return (
    <Button href="#support" startIcon={<Help />}>
      Help
    </Button>
  );
}
