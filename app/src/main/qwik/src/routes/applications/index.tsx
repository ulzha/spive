import { component$ } from '@builder.io/qwik';
import type { DocumentHead } from '@builder.io/qwik-city';
import Legend from '~/components/app/timeline/legend';

export default component$(() => (
  <div class="padding">
    <div class="titlebar">
      <h4 class="MuiTypography-root-2141 MuiTypography-h4-2149">Event-Driven Applications</h4>
      <div class="rightpanel">
        <button class="MuiButtonBase-root-2191 MuiButton-root-2201 MuiButton-text-2203"><span class="MuiButton-label-2202">Create new</span><span class="MuiTouchRipple-root-2322"></span></button>
      </div>
    </div>
    <div class="MuiPaper-root-2233 MuiPaper-elevation1-2237 MuiPaper-rounded-2234">

      <div class="MuiCardHeader-root-2261" style="display: inline-block; width: 100%">
        <div class="MuiCardHeader-content-2264"><span class="MuiTypography-root-2141 MuiCardHeader-title-2265 MuiTypography-h5-2150 MuiTypography-displayBlock-2170">Your Event-Driven Applications</span></div>
        <span id="legend-toggle">LEGEND</span>
      </div>
      <Legend />

      <div class="MuiCardContent-root-2267">

        <button class="MuiButtonBase-root-1541 MuiIconButton-root-1558 more-vert"><span class="MuiIconButton-label-1565"><span class="material-icons MuiIcon-root-1679">more_vert</span></span><span class="MuiTouchRipple-root-1785"></span></button>

        <table class="MuiTable-root-2332">

          <thead class="MuiTableHead-root-2334">
            <tr class="MuiTableRow-head-2337">
              <th class="MuiTableCell-root-2339 MuiTableCell-head-2340" scope="col">NAME</th>
              <th class="MuiTableCell-root-2339 MuiTableCell-head-2340" scope="col">TIMELINE</th>
              {/* <th class="MuiTableCell-root-2339 MuiTableCell-head-2340" scope="col">DATAFLOW URL</th> */}
              {/* <th class="MuiTableCell-root-2339 MuiTableCell-head-2340" scope="col">DEPLOYMENT LOG URL</th> */}
            </tr>
          </thead>

          <tbody id="applications">
          </tbody>

        </table>

      </div>
    </div>
  </div>
));

export const head: DocumentHead = {
  title: 'Applications',
  meta: [
    {
      name: 'description',
      content: 'Main landing page for observing application status live',
    },
  ],
};
