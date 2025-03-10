/** @jsxImportSource react */

import { useState } from "react";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormGroup,
  TextField,
} from "@mui/material";

export interface NewApplicationFormProps {
  onNew: ({}: { name: string; version: string }) => any;
}

export default function NewApplicationForm({ onNew }: NewApplicationFormProps) {
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

  return (
    <>
      <Button variant="outlined" onClick={showDialog}>
        New...
      </Button>
      <Dialog open={open} onClose={hideDialog}>
        <DialogTitle>Create New Application</DialogTitle>
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
          <br />
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
          <FormGroup row>
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
            &nbsp;or&nbsp;{/* <label htmlFor="artifact">choose file:</label> */}
            <TextField
              margin="dense"
              id="artifact"
              type="file"
              variant="standard"
              // inputProps={{ className: "hide-accessibly" }}
            />
            {/* TODO or - code from scratch, start writing in a codespace or local IDE... */}
          </FormGroup>
        </DialogContent>
        <DialogActions>
          <Button onClick={hideDialog}>Cancel</Button>
          <Button
            variant="contained"
            onClick={() => {
              hideDialog();
              onNew({
                name: name,
                version: version,
                artifactUrl: artifactUrl,
                availabilityZones: ["dev-1"],
                inputStreamIds: ["321f31ba-30b8-4cb2-8564-3b4302fcb5ec"],
                outputStreamIds: ["321f31ba-30b8-4cb2-8564-3b4302fcb5ec"],
              });
            }}
          >
            Deploy
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
