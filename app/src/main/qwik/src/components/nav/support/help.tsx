/** @jsxImportSource react */

import { Button } from "@mui/material";
import HelpIcon from "@mui/icons-material/Help";

export default function HelpButton() {
  return (
    <Button href="#support" startIcon={<HelpIcon />}>
      Help
    </Button>
  );
}
