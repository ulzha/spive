/** @jsxImportSource react */

import { useState } from "react";
import { qwikify$ } from "@builder.io/qwik-react";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";

export const MUICreateNewApplicationForm = qwikify$(
  ({ onCreate }: { onCreate: ({}) => any }) => {
    const [open, setOpen] = useState(false);
    const [name, setName] = useState("");
    const [version, setVersion] = useState("");
    const [artifactUrl, setArtifactUrl] = useState("");

    const showDialog = () => {
      setOpen(true);
    };

    const hideDialog = () => {
      setOpen(false);
    };

    const createApplication = () => {
      hideDialog();
      onCreate({
        name: name,
        version: version,
        artifactUrl: artifactUrl,
        availabilityZones: ["dev-1"],
        inputStreamIds: ["321f31ba-30b8-4cb2-8564-3b4302fcb5ec"],
        outputStreamIds: ["321f31ba-30b8-4cb2-8564-3b4302fcb5ec"],
      });
    };

    return (
      <div>
        <Button variant="outlined" onClick={showDialog}>
          Create New
        </Button>
        <Dialog open={open} onClose={hideDialog}>
          <DialogTitle>Create New</DialogTitle>
          <DialogContent>
            <DialogContentText>
              Currently only file:// urls are supported. More clarification text goes here goes here goes here.
            </DialogContentText>
            <TextField
              autoFocus
              margin="dense"
              id="name"
              label="Name"
              type="text"
              variant="standard"
              onChange={(e) => {
                setName(e.target.value);
              }}
            />
            <TextField
              autoFocus
              margin="dense"
              id="version"
              label="Version"
              type="text"
              variant="standard"
              onChange={(e) => {
                setVersion(e.target.value);
              }}
            />
            <TextField
              margin="dense"
              id="artifactUrl"
              label="Artifact URL"
              type="url"
              variant="standard"
              onChange={(e) => {
                setArtifactUrl(e.target.value);
              }}
            />
            Or choose file
            <TextField margin="dense" id="artifact" label="Artifact" type="file" variant="standard" />
          </DialogContent>
          <DialogActions>
            <Button onClick={hideDialog}>Cancel</Button>
            <Button onClick={createApplication}>Deploy</Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  },
  { eagerness: "hover" }
);
